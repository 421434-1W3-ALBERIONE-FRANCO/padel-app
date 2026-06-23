package com.padel.service.impl;

import com.padel.domain.entity.*;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.ReservaRequest;
import com.padel.dto.response.ReservaResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.exception.ReservaNoModificableException;
import com.padel.exception.SlotNoDisponibleException;
import com.padel.mapper.ReservaMapper;
import com.padel.repository.*;
import com.padel.service.NotificacionService;
import com.padel.service.RedisLockService;
import com.padel.service.ReservaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
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
    private final ReservaMapper reservaMapper;

    @Override
    public ReservaResponse crearReserva(ReservaRequest request, String usuarioEmail) {
        log.info("Iniciando creación de reserva para usuario {} en cancha {} y franja {}", 
                usuarioEmail, request.canchaId(), request.franjaId());

        Usuario usuario = usuarioRepository.findByEmail(usuarioEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + usuarioEmail));

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

        // Validar día aplicable
        DayOfWeek dayOfWeek = fecha.getDayOfWeek();
        Set<DayOfWeek> applicableDays = Arrays.stream(franja.getDiasAplicables().split(","))
                .map(String::trim)
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
        if (!applicableDays.contains(dayOfWeek)) {
            throw new SlotNoDisponibleException("La franja horaria no está disponible para el día de la semana especificado");
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
                    .anyMatch(b -> franja.getHoraInicio().isBefore(b.getHoraHasta()) && b.getHoraDesde().isBefore(franja.getHoraFin()));
            if (isBlocked) {
                throw new SlotNoDisponibleException("La cancha está bloqueada en ese horario");
            }

            // 2. Evaluar si existe una Reserva CONFIRMADA o COMPLETADA
            List<Reserva> activeReservations = reservaRepository.findActiveReservationsByCanchaAndDate(cancha.getId(), fecha);
            boolean isReserved = activeReservations.stream()
                    .anyMatch(r -> franja.getHoraInicio().isBefore(r.getHoraFin()) && r.getHoraInicio().isBefore(franja.getHoraFin()));
            if (isReserved) {
                throw new SlotNoDisponibleException("La cancha ya está reservada en ese horario");
            }

            // Determinar si es nocturno (18:00 en adelante)
            boolean esNocturno = !franja.getHoraInicio().isBefore(LocalTime.of(18, 0));
            BigDecimal precioTotal = esNocturno ? franja.getPrecioNocturno() : franja.getPrecioBase();

            Reserva reserva = Reserva.builder()
                    .usuario(usuario)
                    .cancha(cancha)
                    .franjaHoraria(franja)
                    .fecha(fecha)
                    .horaInicio(franja.getHoraInicio())
                    .horaFin(franja.getHoraFin())
                    .precioTotal(precioTotal)
                    .estadoReserva(EstadoReserva.PENDIENTE_PAGO)
                    .origen(request.origen())
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
        if (reserva.getEstadoReserva() == EstadoReserva.COMPLETADA || reserva.getEstadoReserva() == EstadoReserva.CANCELADA) {
            throw new ReservaNoModificableException("No se puede cancelar una reserva que ya está en estado " + reserva.getEstadoReserva());
        }

        // Cancelar la reserva
        reserva.setEstadoReserva(EstadoReserva.CANCELADA);
        reserva.setMotivoCancelacion(motivo != null ? motivo : "Cancelada por el usuario");
        Reserva updatedReserva = reservaRepository.save(reserva);

        // Liberar el lock de Redis
        redisLockService.releaseLock(reserva.getCancha().getId(), reserva.getFecha(), reserva.getHoraInicio());

        // Enviar notificación asíncrona por WhatsApp
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
        }
    }
}
