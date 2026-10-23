package com.padel.service.impl;

import com.padel.config.LimitesProperties;
import com.padel.config.ReservasProperties;
import com.padel.domain.entity.BloqueoCancha;
import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.OrigenReserva;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.domain.evento.DisponibilidadCambiadaEvent;
import com.padel.domain.evento.OrigenCancelacion;
import com.padel.domain.evento.ReservaCanceladaEvent;
import com.padel.domain.evento.ReservaConfirmadaEvent;
import com.padel.domain.valueobject.RangoHorario;
import com.padel.dto.request.ReservaRequest;
import com.padel.dto.response.PaginaResponse;
import com.padel.dto.response.ReservaResponse;
import com.padel.exception.DemasiadosIntentosException;
import com.padel.exception.EstadoInvalidoException;
import com.padel.exception.ReservaNoModificableException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.exception.SlotNoDisponibleException;
import com.padel.mapper.ReservaMapper;
import com.padel.repository.BloqueoCanchaRepository;
import com.padel.repository.CanchaRepository;
import com.padel.repository.FranjaHorariaRepository;
import com.padel.repository.ReservaRepository;
import com.padel.repository.ReservaSpecs;
import com.padel.repository.UsuarioRepository;
import com.padel.security.ControlAcceso;
import com.padel.security.LimitadorDePedidos;
import com.padel.security.MensajesLimite;
import com.padel.service.NotificacionInAppService;
import com.padel.service.NotificacionService;
import com.padel.service.RedisLockService;
import com.padel.service.ReintegroService;
import com.padel.service.ReservaService;
import com.padel.util.EmailUtil;
import com.padel.util.FechaHoraUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReservaServiceImpl implements ReservaService {

    private static final String DURACION_EXCEDE_HORARIO = "La duración seleccionada excede el horario disponible de la cancha";

    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CanchaRepository canchaRepository;
    private final FranjaHorariaRepository franjaHorariaRepository;
    private final BloqueoCanchaRepository bloqueoCanchaRepository;
    private final RedisLockService redisLockService;
    private final NotificacionService notificacionService;
    private final NotificacionInAppService notificacionInAppService;
    private final ReintegroService reintegroService;
    private final CalculadoraPrecioReserva calculadoraPrecio;
    private final ReservasProperties reservasProperties;
    private final ControlAcceso controlAcceso;
    private final LimitadorDePedidos limitador;
    private final LimitesProperties limites;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transactionTemplate;
    private final ReservaMapper reservaMapper;

    @Override
    public ReservaResponse crearReserva(ReservaRequest request, String usuarioEmail) {
        Usuario solicitante = buscarUsuario(usuarioEmail);
        boolean esStaff = controlAcceso.esStaff(solicitante);
        log.info("Creando reserva: solicitante ID {}, cancha {}, franja {}", solicitante.getId(), request.canchaId(), request.franjaId());

        // Alta rápida por staff: si viene usuarioId, la reserva es para ese cliente.
        Usuario titular = solicitante;
        if (request.usuarioId() != null && esStaff) {
            titular = usuarioRepository.findById(request.usuarioId())
                    .filter(Usuario::isActivo)
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + request.usuarioId()));
        }

        // Bloquea la fila de la cancha hasta el commit: dos altas simultáneas sobre la misma cancha se ejecutan
        // de a una y la segunda ve la reserva de la primera. La restricción EXCLUDE de la base (V34) es la
        // garantía final ante cualquier otro camino.
        Cancha cancha = canchaRepository.findByIdForUpdate(request.canchaId())
                .filter(Cancha::isActiva)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha no encontrada o inactiva"));

        FranjaHoraria franja = franjaHorariaRepository.findById(request.franjaId())
                .orElseThrow(() -> new ResourceNotFoundException("Franja horaria no encontrada"));
        if (!franja.getCancha().getId().equals(cancha.getId())) {
            throw new ResourceNotFoundException("La franja horaria no pertenece a la cancha especificada");
        }

        LocalDate fecha = request.fecha();
        validarFechaReservable(fecha, franja.getHoraInicio());
        if (!franja.aplicaEn(fecha.getDayOfWeek())) {
            throw new SlotNoDisponibleException("La franja horaria no está disponible para el día de la semana especificado");
        }

        int duracion = request.duracionMin() != null ? request.duracionMin() : franja.getDuracionMin();
        if (request.duracionMin() != null && !cancha.duracionesPermitidasSet().contains(duracion)) {
            throw new SlotNoDisponibleException("La duración de " + duracion + " min no está permitida para esta cancha");
        }
        // Un turno que pasa la medianoche (21:30 + 180 min) quedaba con fin < inicio: salía a $0, no chocaba con
        // ninguna reserva y, pagado con bono, le sumaba horas al bono.
        if (!RangoHorario.cabeEnElDia(franja.getHoraInicio(), duracion)) {
            throw new SlotNoDisponibleException(DURACION_EXCEDE_HORARIO);
        }
        RangoHorario rango = RangoHorario.desde(franja.getHoraInicio(), duracion);

        List<FranjaHoraria> franjasDelDia = calculadoraPrecio.franjasDelDia(
                franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(cancha.getId()), fecha.getDayOfWeek());
        BigDecimal precioTotal = calculadoraPrecio.calcular(franjasDelDia, rango)
                .orElseThrow(() -> new SlotNoDisponibleException(DURACION_EXCEDE_HORARIO));

        if (!esStaff) {
            validarLimitePendientes(titular);
            validarRitmoDeAltas(titular);
        }

        if (!redisLockService.acquireLock(cancha.getId(), fecha, rango.inicio())) {
            throw new SlotNoDisponibleException("El slot se encuentra temporalmente bloqueado en proceso de pago");
        }
        try {
            validarSinBloqueos(cancha.getId(), fecha, rango);
            validarSinSuperposicion(cancha.getId(), fecha, rango);

            // El origen es un dato de auditoría: un JUGADOR siempre reserva desde la APP.
            OrigenReserva origen = esStaff && request.origen() != null ? request.origen() : OrigenReserva.APP;

            Reserva reserva = Reserva.builder()
                    .usuario(titular)
                    .cancha(cancha)
                    .franjaHoraria(franja)
                    .fecha(fecha)
                    .horaInicio(rango.inicio())
                    .horaFin(rango.fin())
                    .precioTotal(precioTotal)
                    .origen(origen)
                    .build();

            Reserva guardada = reservaRepository.saveAndFlush(reserva);
            eventos.publishEvent(new DisponibilidadCambiadaEvent(cancha.getId()));
            log.info("Reserva ID {} creada en estado PENDIENTE_PAGO", guardada.getId());
            return reservaMapper.toResponse(guardada);
        } catch (DataIntegrityViolationException e) {
            redisLockService.releaseLock(cancha.getId(), fecha, rango.inicio());
            throw new SlotNoDisponibleException("La cancha ya está reservada (o en proceso de pago) en ese horario");
        } catch (RuntimeException e) {
            redisLockService.releaseLock(cancha.getId(), fecha, rango.inicio());
            throw e;
        }
    }

    private void validarFechaReservable(LocalDate fecha, LocalTime horaInicio) {
        LocalDate hoy = LocalDate.now();
        if (fecha.isBefore(hoy)) {
            throw new SlotNoDisponibleException("La fecha de reserva no puede ser en el pasado");
        }
        if (fecha.equals(hoy) && horaInicio.isBefore(LocalTime.now())) {
            throw new SlotNoDisponibleException("La hora de inicio de la reserva no puede ser en el pasado");
        }
        if (fecha.isAfter(FechaHoraUtil.fechaMaximaReservable(hoy))) {
            throw new SlotNoDisponibleException("Las reservas solo se habilitan dentro del mes en curso (o el siguiente, en la última semana del mes)");
        }
    }

    private void validarLimitePendientes(Usuario titular) {
        long pendientes = reservaRepository.countByUsuarioIdAndEstadoReserva(titular.getId(), EstadoReserva.PENDIENTE_PAGO);
        int maximo = reservasProperties.getMaxPendientesPorJugador();
        if (pendientes >= maximo) {
            throw new EstadoInvalidoException("Ya tenés " + pendientes + (pendientes == 1 ? " reserva pendiente" : " reservas pendientes")
                    + " de pago. Pagá o cancelá alguna antes de reservar otra.");
        }
    }

    // Además del tope de pendientes: un jugador no puede crear (y cancelar) reservas en ráfaga.
    private void validarRitmoDeAltas(Usuario titular) {
        if (!limites.isHabilitado()) {
            return;
        }
        LimitesProperties.Regla regla = limites.getReservasPorJugador();
        long espera = limitador.consumir("reservas:" + titular.getId(), regla.getMaximo(), regla.getVentana());
        if (espera > 0) {
            throw new DemasiadosIntentosException("Hiciste demasiadas reservas seguidas. Probá de nuevo en "
                    + MensajesLimite.duracion(espera) + ".", espera);
        }
    }

    private void validarSinBloqueos(Long canchaId, LocalDate fecha, RangoHorario rango) {
        List<BloqueoCancha> bloqueos = bloqueoCanchaRepository.findBlocksForDate(canchaId, fecha);
        if (bloqueos.stream().anyMatch(b -> rango.seSuperponeCon(b.getHoraDesde(), b.getHoraHasta()))) {
            throw new SlotNoDisponibleException("La cancha está bloqueada en ese horario");
        }
    }

    private void validarSinSuperposicion(Long canchaId, LocalDate fecha, RangoHorario rango) {
        List<Reserva> activas = reservaRepository.findActiveReservationsByCanchaAndDate(canchaId, fecha);
        if (activas.stream().anyMatch(r -> rango.seSuperponeCon(r.getHoraInicio(), r.getHoraFin()))) {
            throw new SlotNoDisponibleException("La cancha ya está reservada (o en proceso de pago) en ese horario");
        }
    }

    @Override
    public ReservaResponse cancelarReserva(Long id, String usuarioEmail, String motivo) {
        Reserva reserva = reservaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + id));
        Usuario usuario = buscarUsuario(usuarioEmail);
        boolean esStaff = controlAcceso.esStaff(usuario);
        controlAcceso.exigirDuenoOStaff(usuario, reserva.getUsuario(), "Reserva");
        log.info("Cancelación de la reserva ID {} pedida por el usuario ID {}", id, usuario.getId());

        if (!reserva.getEstadoReserva().puedePasarA(EstadoReserva.CANCELADA)) {
            throw new ReservaNoModificableException("No se puede cancelar una reserva que ya está en estado " + reserva.getEstadoReserva());
        }
        // Un jugador no puede cancelar un turno que ya empezó o ya se jugó (sigue CONFIRMADA hasta que el scheduler
        // la pasa a COMPLETADA). El staff sí puede, para corregir la agenda.
        if (!esStaff && !reserva.inicio().isAfter(LocalDateTime.now())) {
            throw new ReservaNoModificableException("No se puede cancelar un turno que ya comenzó o finalizó");
        }

        String motivoFinal = motivo != null && !motivo.isBlank()
                ? motivo.trim()
                : (esStaff ? "Cancelada por el club" : "Cancelada por el usuario");
        return reservaMapper.toResponse(cancelar(reserva, motivoFinal, esStaff ? OrigenCancelacion.CLUB : OrigenCancelacion.JUGADOR));
    }

    @Override
    public void cancelarPorClub(Long id, String motivo) {
        Reserva reserva = reservaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + id));
        if (reserva.getEstadoReserva().puedePasarA(EstadoReserva.CANCELADA)) {
            cancelar(reserva, motivo, OrigenCancelacion.CLUB);
        }
    }

    private Reserva cancelar(Reserva reserva, String motivo, OrigenCancelacion origen) {
        reserva.cancelar(motivo);
        Reserva guardada = reservaRepository.save(reserva);
        redisLockService.releaseLock(reserva.getCancha().getId(), reserva.getFecha(), reserva.getHoraInicio());

        String detalleReintegro = reintegroService.reintegrarCancelacion(guardada, origen);
        prepararParaNotificacion(guardada);
        eventos.publishEvent(new ReservaCanceladaEvent(guardada, origen, detalleReintegro));
        eventos.publishEvent(new DisponibilidadCambiadaEvent(guardada.getCancha().getId()));
        log.info("Reserva ID {} cancelada ({})", guardada.getId(), origen);
        return guardada;
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaResponse obtenerPorId(Long id, String usuarioEmail) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + id));
        controlAcceso.exigirDuenoOStaff(buscarUsuario(usuarioEmail), reserva.getUsuario(), "Reserva");
        return reservaMapper.toResponse(reserva);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponse> obtenerMisReservas(String usuarioEmail) {
        Usuario usuario = buscarUsuario(usuarioEmail);
        return reservaRepository.findByUsuarioIdOrderByFechaDesc(usuario.getId()).stream()
                .map(reservaMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<ReservaResponse> buscar(LocalDate desde, LocalDate hasta, EstadoReserva estado, String texto, Pageable pageable) {
        return PaginaResponse.de(
                reservaRepository.findAll(ReservaSpecs.filtrar(desde, hasta, estado, texto), pageable),
                reservaMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponse> obtenerPorFecha(LocalDate fecha) {
        return reservaRepository.findDelDia(fecha).stream()
                .map(reservaMapper::toResponse)
                .toList();
    }

    /**
     * Cada reserva vencida se cancela en su propia transacción: si el webhook de pago la confirma al mismo
     * tiempo, el bloqueo optimista hace fallar solo esa (y queda confirmada) sin frenar al resto.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void limpiarReservasExpiradas() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(reservasProperties.getMinutosParaPagar());
        List<Long> vencidas = reservaRepository.findByEstadoReservaAndCreatedAtBefore(EstadoReserva.PENDIENTE_PAGO, limite)
                .stream().map(Reserva::getId).toList();
        if (vencidas.isEmpty()) {
            return;
        }
        log.info("Cancelando {} reservas sin pago creadas antes de {}", vencidas.size(), limite);
        for (Long id : vencidas) {
            try {
                transactionTemplate.executeWithoutResult(status -> reservaRepository.findByIdForUpdate(id)
                        .filter(r -> r.getEstadoReserva() == EstadoReserva.PENDIENTE_PAGO)
                        .ifPresent(r -> cancelar(r, "Expiración del tiempo de pago (" + reservasProperties.getMinutosParaPagar() + " minutos)",
                                OrigenCancelacion.FALTA_DE_PAGO)));
            } catch (RuntimeException e) {
                log.warn("No se pudo vencer la reserva ID {}: {}", id, e.getMessage());
            }
        }
    }

    @Override
    public void completarReservasFinalizadas() {
        // Fecha y hora salen del mismo instante: con LocalDate.now() y LocalTime.now() por separado, justo a la
        // medianoche podían quedar de días distintos.
        LocalDateTime ahora = LocalDateTime.now();
        List<Reserva> finalizadas = reservaRepository.findConfirmadasFinalizadas(ahora.toLocalDate(), ahora.toLocalTime());
        if (finalizadas.isEmpty()) {
            return;
        }
        finalizadas.forEach(Reserva::completar);
        reservaRepository.saveAll(finalizadas);
        log.info("Reservas marcadas como COMPLETADA por turno finalizado: {}", finalizadas.size());
    }

    @Override
    public void notificarReservasDeHoy() {
        LocalDate hoy = LocalDate.now();
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
            notificacionService.enviarRecordatorio(r);
            r.setRecordatorioEnviado(true);
            reservaRepository.save(r);
        }
        log.info("Recordatorios de turnos de hoy enviados: {}", reservasDeHoy.size());
    }

    @Override
    public ReservaResponse confirmarReserva(Long id) {
        Reserva reserva = reservaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + id));

        if (reserva.getEstadoReserva() == EstadoReserva.CONFIRMADA) {
            log.info("La reserva ID {} ya se encuentra CONFIRMADA", id);
            return reservaMapper.toResponse(reserva);
        }
        reserva.confirmar();
        Reserva confirmada = reservaRepository.save(reserva);
        redisLockService.releaseLock(reserva.getCancha().getId(), reserva.getFecha(), reserva.getHoraInicio());

        prepararParaNotificacion(confirmada);
        eventos.publishEvent(new ReservaConfirmadaEvent(confirmada));
        eventos.publishEvent(new DisponibilidadCambiadaEvent(confirmada.getCancha().getId()));
        log.info("Reserva ID {} confirmada", id);
        return reservaMapper.toResponse(confirmada);
    }

    // Los avisos leen usuario y cancha (LAZY): se inicializan dentro de la transacción.
    private void prepararParaNotificacion(Reserva reserva) {
        Hibernate.initialize(reserva.getUsuario());
        Hibernate.initialize(reserva.getCancha());
    }

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.findByEmail(EmailUtil.normalizar(email))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }
}
