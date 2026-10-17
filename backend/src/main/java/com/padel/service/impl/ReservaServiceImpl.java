package com.padel.service.impl;

import com.padel.domain.entity.*;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.OrigenReserva;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.dto.request.ReservaRequest;
import com.padel.dto.response.ReservaResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.exception.ReservaNoModificableException;
import com.padel.exception.SlotNoDisponibleException;
import com.padel.mapper.ReservaMapper;
import com.padel.repository.*;
import com.padel.service.NotificacionInAppService;
import com.padel.service.NotificacionService;
import com.padel.service.RedisLockService;
import com.padel.service.ReservaService;
import com.padel.util.FechaHoraUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReservaServiceImpl implements ReservaService {

    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CanchaRepository canchaRepository;
    private final FranjaHorariaRepository franjaHorariaRepository;
    private final BloqueoCanchaRepository bloqueoCanchaRepository;
    private final RedisLockService redisLockService;
    private final NotificacionService notificacionService;
    private final NotificacionInAppService notificacionInAppService;
    private final ReservaMapper reservaMapper;

    @Override
    public ReservaResponse crearReserva(ReservaRequest request, String usuarioEmail) {
        log.info("Iniciando creación de reserva para usuario {} en cancha {} y franja {}", 
                usuarioEmail, request.canchaId(), request.franjaId());

        Usuario solicitante = usuarioRepository.findByEmail(usuarioEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + usuarioEmail));

        // Alta rápida por staff: si viene usuarioId y el solicitante es staff, la reserva es para ese cliente.
        Usuario usuario = solicitante;
        boolean esStaff = solicitante.getRol() == RolUsuario.ADMIN || solicitante.getRol() == RolUsuario.RECEPCIONISTA;
        if (request.usuarioId() != null && esStaff) {
            usuario = usuarioRepository.findById(request.usuarioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + request.usuarioId()));
        }

        Cancha cancha = canchaRepository.findById(request.canchaId())
                .filter(Cancha::isActiva)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha no encontrada o inactiva"));

        FranjaHoraria franja = franjaHorariaRepository.findById(request.franjaId())
                .orElseThrow(() -> new ResourceNotFoundException("Franja horaria no encontrada"));

        // Validar que la franja pertenezca a la cancha
        if (!franja.getCancha().getId().equals(cancha.getId())) {
            throw new ResourceNotFoundException("La franja horaria no pertenece a la cancha especificada");
        }

        // Validar que la fecha sea válida
        LocalDate fecha = request.fecha();
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        if (fecha.isBefore(today)) {
            throw new SlotNoDisponibleException("La fecha de reserva no puede ser en el pasado");
        }
        if (fecha.equals(today) && franja.getHoraInicio().isBefore(now)) {
            throw new SlotNoDisponibleException("La hora de inicio de la reserva no puede ser en el pasado");
        }
        if (fecha.isAfter(calcularFechaMaximaReservable(today))) {
            throw new SlotNoDisponibleException("Las reservas solo se habilitan dentro del mes en curso (o el siguiente, en la última semana del mes)");
        }

        // Validar día aplicable
        DayOfWeek dayOfWeek = fecha.getDayOfWeek();
        Set<DayOfWeek> applicableDays = Arrays.stream(franja.getDiasAplicables().split(","))
                .map(String::trim)
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
        if (!applicableDays.contains(dayOfWeek)) {
            throw new SlotNoDisponibleException("La franja horaria no está disponible para el día de la semana especificado");
        }

        // === Duración variable ===
        int duracion = request.duracionMin() != null ? request.duracionMin() : franja.getDuracionMin();
        if (request.duracionMin() != null && !parseDuraciones(cancha.getDuracionesPermitidas()).contains(duracion)) {
            throw new SlotNoDisponibleException("La duración de " + duracion + " min no está permitida para esta cancha");
        }
        LocalTime horaInicio = franja.getHoraInicio();
        LocalTime horaFin = horaInicio.plusMinutes(duracion);

        // Franjas del día que la reserva cubre; se valida cobertura contigua (dentro del horario de apertura).
        List<FranjaHoraria> franjasDia = franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(cancha.getId()).stream()
                .filter(f -> Arrays.stream(f.getDiasAplicables().split(","))
                        .map(String::trim).map(DayOfWeek::valueOf).collect(Collectors.toSet()).contains(dayOfWeek))
                .sorted(Comparator.comparing(FranjaHoraria::getHoraInicio))
                .toList();
        List<FranjaHoraria> cubiertas = franjasDia.stream()
                .filter(f -> f.getHoraInicio().isBefore(horaFin) && horaInicio.isBefore(f.getHoraFin()))
                .toList();
        LocalTime cursor = horaInicio;
        for (FranjaHoraria f : cubiertas) {
            if (f.getHoraInicio().isAfter(cursor)) break; // hay un hueco entre franjas
            cursor = f.getHoraFin();
        }
        if (cursor.isBefore(horaFin)) {
            throw new SlotNoDisponibleException("La duración seleccionada excede el horario disponible de la cancha");
        }

        // Intentar adquirir el lock en Redis
        boolean lockAcquired = redisLockService.acquireLock(cancha.getId(), fecha, franja.getHoraInicio());
        if (!lockAcquired) {
            throw new SlotNoDisponibleException("El slot se encuentra temporalmente bloqueado en proceso de pago");
        }

        try {
            // 1. Evaluar si hay un BloqueoCancha activo
            List<BloqueoCancha> blocks = bloqueoCanchaRepository.findBlocksForDate(cancha.getId(), fecha);
            boolean isBlocked = blocks.stream()
                    .anyMatch(b -> horaInicio.isBefore(b.getHoraHasta()) && b.getHoraDesde().isBefore(horaFin));
            if (isBlocked) {
                throw new SlotNoDisponibleException("La cancha está bloqueada en ese horario");
            }

            // 2. Evaluar si existe una Reserva pendiente de pago, CONFIRMADA o COMPLETADA (solapamiento en todo el rango)
            List<Reserva> activeReservations = reservaRepository.findActiveReservationsByCanchaAndDate(cancha.getId(), fecha);
            boolean isReserved = activeReservations.stream()
                    .anyMatch(r -> horaInicio.isBefore(r.getHoraFin()) && r.getHoraInicio().isBefore(horaFin));
            if (isReserved) {
                throw new SlotNoDisponibleException("La cancha ya está reservada (o en proceso de pago) en ese horario");
            }

            // Precio prorrateado por las franjas que cubre (cada tramo con su tarifa base/nocturna).
            BigDecimal precioTotal = calcularPrecioProrrateado(cubiertas, horaInicio, horaFin);

            // El origen es un dato de auditoría: si el SOLICITANTE es JUGADOR siempre es APP;
            // sólo staff (admin/recepción) puede marcar RECEPCION (incluye altas para clientes).
            OrigenReserva origen = solicitante.getRol() == RolUsuario.JUGADOR ? OrigenReserva.APP : request.origen();

            Reserva reserva = Reserva.builder()
                    .usuario(usuario)
                    .cancha(cancha)
                    .franjaHoraria(franja)
                    .fecha(fecha)
                    .horaInicio(horaInicio)
                    .horaFin(horaFin)
                    .precioTotal(precioTotal)
                    .estadoReserva(EstadoReserva.PENDIENTE_PAGO)
                    .origen(origen)
                    .build();

            Reserva savedReserva = reservaRepository.save(reserva);
            log.info("Reserva creada exitosamente con ID {} en estado PENDIENTE_PAGO", savedReserva.getId());

            return reservaMapper.toResponse(savedReserva);
        } catch (Exception e) {
            log.warn("Error durante la creación de la reserva. Liberando lock de Redis para cancha {} en fecha {} hora {}", 
                    cancha.getId(), fecha, franja.getHoraInicio());
            redisLockService.releaseLock(cancha.getId(), fecha, franja.getHoraInicio());
            throw e;
        }
    }

    // Parsea "60,90,120" -> {60,90,120}.
    private Set<Integer> parseDuraciones(String s) {
        if (s == null || s.isBlank()) {
            return Set.of(90);
        }
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .map(Integer::valueOf)
                .collect(Collectors.toSet());
    }

    // Precio prorrateado: por cada franja cubierta, su tarifa (base/nocturna según su hora de inicio)
    // multiplicada por la fracción de la franja que la reserva ocupa. Cruza bien el corte nocturno (18:00).
    private BigDecimal calcularPrecioProrrateado(List<FranjaHoraria> cubiertas, LocalTime horaInicio, LocalTime horaFin) {
        BigDecimal total = BigDecimal.ZERO;
        for (FranjaHoraria f : cubiertas) {
            LocalTime overlapStart = horaInicio.isAfter(f.getHoraInicio()) ? horaInicio : f.getHoraInicio();
            LocalTime overlapEnd = horaFin.isBefore(f.getHoraFin()) ? horaFin : f.getHoraFin();
            long overlapMin = Duration.between(overlapStart, overlapEnd).toMinutes();
            if (overlapMin <= 0) continue;
            boolean noct = !f.getHoraInicio().isBefore(LocalTime.of(18, 0));
            BigDecimal precioFranja = noct ? f.getPrecioNocturno() : f.getPrecioBase();
            BigDecimal fraccion = BigDecimal.valueOf(overlapMin)
                    .divide(BigDecimal.valueOf(f.getDuracionMin()), 6, RoundingMode.HALF_UP);
            total = total.add(precioFranja.multiply(fraccion));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    // Reservas habilitadas solo dentro del mes en curso; en la última semana del mes
    // se habilita también el mes siguiente completo (ej. fin de diciembre -> reservas de enero).
    private LocalDate calcularFechaMaximaReservable(LocalDate hoy) {
        return FechaHoraUtil.fechaMaximaReservable(hoy);
    }

    @Override
    public ReservaResponse cancelarReserva(Long id, String usuarioEmail, String motivo) {
        log.info("Iniciando cancelación de reserva ID {} por usuario {}", id, usuarioEmail);

        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + id));

        Usuario usuario = usuarioRepository.findByEmail(usuarioEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + usuarioEmail));

        // Validar permisos: solo el dueño de la reserva o admin/recepcionista puede cancelarla
        boolean isAdminOrRecepcion = usuario.getRol() == RolUsuario.ADMIN || usuario.getRol() == RolUsuario.RECEPCIONISTA;
        if (!isAdminOrRecepcion && !reserva.getUsuario().getId().equals(usuario.getId())) {
            throw new ResourceNotFoundException("Reserva no encontrada para el usuario especificado");
        }

        // Validar estado actual de la reserva
        if (reserva.getEstadoReserva() == EstadoReserva.COMPLETADA
                || reserva.getEstadoReserva() == EstadoReserva.CANCELADA
                || reserva.getEstadoReserva() == EstadoReserva.NO_SHOW) {
            throw new ReservaNoModificableException("No se puede cancelar una reserva que ya está en estado " + reserva.getEstadoReserva());
        }

        // Un jugador no puede cancelar un turno que ya empezó o ya se jugó (las reservas pasadas siguen
        // CONFIRMADAS porque nada las pasa a COMPLETADA). El staff sí puede, para corregir la agenda.
        if (!isAdminOrRecepcion && !LocalDateTime.of(reserva.getFecha(), reserva.getHoraInicio()).isAfter(LocalDateTime.now())) {
            throw new ReservaNoModificableException("No se puede cancelar un turno que ya comenzó o finalizó");
        }

        // Cancelar la reserva
        reserva.setEstadoReserva(EstadoReserva.CANCELADA);
        reserva.setMotivoCancelacion(motivo != null && !motivo.isBlank() ? motivo : "Cancelada por el usuario");
        Reserva updatedReserva = reservaRepository.save(reserva);

        // Liberar el lock de Redis
        redisLockService.releaseLock(reserva.getCancha().getId(), reserva.getFecha(), reserva.getHoraInicio());

        // Enviar notificación asíncrona por WhatsApp
        prepararParaNotificacionAsync(updatedReserva);
        notificacionService.enviarCancelacion(updatedReserva);

        log.info("Reserva ID {} cancelada exitosamente", id);
        return reservaMapper.toResponse(updatedReserva);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaResponse obtenerPorId(Long id, String usuarioEmail) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + id));

        Usuario usuario = usuarioRepository.findByEmail(usuarioEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + usuarioEmail));

        // Validar permisos
        boolean isAdminOrRecepcion = usuario.getRol() == RolUsuario.ADMIN || usuario.getRol() == RolUsuario.RECEPCIONISTA;
        if (!isAdminOrRecepcion && !reserva.getUsuario().getId().equals(usuario.getId())) {
            throw new ResourceNotFoundException("Reserva no encontrada para el usuario especificado");
        }

        return reservaMapper.toResponse(reserva);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponse> obtenerMisReservas(String usuarioEmail) {
        Usuario usuario = usuarioRepository.findByEmail(usuarioEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + usuarioEmail));

        List<Reserva> reservas = reservaRepository.findByUsuarioIdOrderByFechaDesc(usuario.getId());
        return reservas.stream()
                .map(reservaMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponse> obtenerTodas() {
        return reservaRepository.findAll().stream()
                .map(reservaMapper::toResponse)
                .toList();
    }

    @Override
    public void limpiarReservasExpiradas() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        log.info("Ejecutando limpieza de reservas huérfanas pendientes de pago anteriores a {}", threshold);

        List<Reserva> expiradas = reservaRepository.findByEstadoReservaAndCreatedAtBefore(EstadoReserva.PENDIENTE_PAGO, threshold);
        
        for (Reserva r : expiradas) {
            log.info("Cancelando reserva expirada ID {} del usuario {}", r.getId(), r.getUsuario().getEmail());
            r.setEstadoReserva(EstadoReserva.CANCELADA);
            r.setMotivoCancelacion("Expiración del tiempo de pago (15 minutos)");
            reservaRepository.save(r);

            // Liberar el lock de Redis
            redisLockService.releaseLock(r.getCancha().getId(), r.getFecha(), r.getHoraInicio());

            // Aviso in-app (no por WhatsApp/email): la reserva venció por falta de pago a tiempo.
            notificacionInAppService.crear(
                    r.getUsuario(),
                    TipoNotificacion.RESERVA_CANCELADA_FALTA_PAGO,
                    "Reserva cancelada por falta de pago",
                    String.format("Tu reserva del %s de %s a %s fue cancelada automáticamente por no completarse el pago a tiempo.",
                            r.getFecha(), r.getHoraInicio(), r.getHoraFin()),
                    r.getId()
            );
        }
    }

    @Override
    @Transactional
    public void notificarReservasDeHoy() {
        LocalDate hoy = LocalDate.now();
        log.info("Buscando reservas confirmadas para hoy ({}) sin recordatorio enviado", hoy);

        List<Reserva> reservasDeHoy = reservaRepository.findByFechaAndEstadoReservaAndRecordatorioEnviadoFalse(hoy, EstadoReserva.CONFIRMADA);
        for (Reserva r : reservasDeHoy) {
            notificacionInAppService.crear(
                    r.getUsuario(),
                    TipoNotificacion.RECORDATORIO_RESERVA,
                    "Recordatorio de tu turno de hoy",
                    String.format("Te recordamos tu turno de hoy en la cancha %s de %s a %s.",
                            r.getCancha().getNombre(), r.getHoraInicio(), r.getHoraFin()),
                    r.getId()
            );
            r.setRecordatorioEnviado(true);
            reservaRepository.save(r);
        }
        log.info("Recordatorios enviados: {}", reservasDeHoy.size());
    }

    @Override
    public ReservaResponse confirmarReserva(Long id) {
        log.info("Iniciando confirmación de reserva ID {}", id);
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + id));

        if (reserva.getEstadoReserva() == EstadoReserva.CONFIRMADA) {
            log.info("La reserva ID {} ya se encuentra CONFIRMADA", id);
            return reservaMapper.toResponse(reserva);
        }

        if (reserva.getEstadoReserva() != EstadoReserva.PENDIENTE_PAGO) {
            throw new ReservaNoModificableException("No se puede confirmar una reserva en estado " + reserva.getEstadoReserva());
        }

        reserva.setEstadoReserva(EstadoReserva.CONFIRMADA);
        Reserva updatedReserva = reservaRepository.save(reserva);

        // Liberar el lock de Redis
        redisLockService.releaseLock(reserva.getCancha().getId(), reserva.getFecha(), reserva.getHoraInicio());

        // Enviar notificación asíncrona por WhatsApp
        prepararParaNotificacionAsync(updatedReserva);
        notificacionService.enviarConfirmacion(updatedReserva);

        log.info("Reserva ID {} confirmada exitosamente", id);
        return reservaMapper.toResponse(updatedReserva);
    }

    // Las notificaciones de WhatsApp corren en otro hilo (@Async) y leen usuario y cancha, que son LAZY:
    // se inicializan acá, dentro de la transacción, para que ese hilo no use la sesión de Hibernate
    // de éste (no es thread-safe y puede estar cerrada -> LazyInitializationException).
    private void prepararParaNotificacionAsync(Reserva reserva) {
        Hibernate.initialize(reserva.getUsuario());
        Hibernate.initialize(reserva.getCancha());
    }
}
