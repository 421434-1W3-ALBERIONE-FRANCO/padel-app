package com.padel.service.impl;

import com.padel.config.ReservasProperties;
import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.ConceptoPago;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.MetodoPago;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.dto.response.PagoResponse;
import com.padel.dto.response.PreferenciaResponse;
import com.padel.exception.DatosInvalidosException;
import com.padel.exception.EstadoInvalidoException;
import com.padel.exception.ReservaNoModificableException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.integracion.pagos.PasarelaPagos.NuevaPreferencia;
import com.padel.integracion.pagos.PasarelaPagos.PagoExterno;
import com.padel.integracion.pagos.PasarelaPagos.PreferenciaCreada;
import com.padel.integracion.pagos.PasarelaPagos;
import com.padel.mapper.PagoMapper;
import com.padel.repository.PagoRepository;
import com.padel.repository.ReservaRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.security.ControlAcceso;
import com.padel.service.ConsumoService;
import com.padel.service.EmailService;
import com.padel.service.NotificacionInAppService;
import com.padel.service.PagoService;
import com.padel.service.ReintegroService;
import com.padel.service.ReservaService;
import com.padel.util.EmailUtil;
import com.padel.util.MonedaUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PagoServiceImpl implements PagoService {

    /** Referencia externa de un cobro de consumos enviado al Point: "CONSUMOS-{idPago}". */
    static final String PREFIJO_CONSUMOS = "CONSUMOS-";
    private static final String MONEDA = "ARS";

    private final PagoRepository pagoRepository;
    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReservaService reservaService;
    private final ConsumoService consumoService;
    private final ReintegroService reintegroService;
    private final PasarelaPagos pasarelaPagos;
    private final PagoMapper pagoMapper;
    private final EmailService emailService;
    private final NotificacionInAppService notificacionInAppService;
    private final ControlAcceso controlAcceso;
    private final ReservasProperties reservasProperties;

    // URL pública del frontend: MercadoPago redirige ahí al terminar el checkout.
    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    public static String referenciaConsumos(Long pagoId) {
        return PREFIJO_CONSUMOS + pagoId;
    }

    @Override
    public PreferenciaResponse crearPreferencia(Long reservaId, String usuarioEmail) {
        Reserva reserva = reservaRepository.findByIdForUpdate(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservaId));
        Usuario usuario = buscarUsuario(usuarioEmail);
        controlAcceso.exigirDuenoOStaff(usuario, reserva.getUsuario(), "Reserva");

        // Sólo se puede generar un cobro para una reserva que sigue esperando pago
        if (reserva.getEstadoReserva() != EstadoReserva.PENDIENTE_PAGO) {
            throw new ReservaNoModificableException("No se puede generar un cobro para una reserva en estado " + reserva.getEstadoReserva());
        }
        if (reserva.getPrecioTotal() == null || reserva.getPrecioTotal().signum() <= 0) {
            throw new DatosInvalidosException("El turno no tiene un importe a cobrar");
        }
        // La preferencia vence junto con la reserva: antes se podía pagar un turno que el sistema ya había liberado.
        LocalDateTime creada = reserva.getCreatedAt() != null ? reserva.getCreatedAt() : LocalDateTime.now();
        LocalDateTime vence = creada.plusMinutes(reservasProperties.getMinutosParaPagar());
        if (!vence.isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new ReservaNoModificableException("El tiempo para pagar esta reserva ya venció");
        }

        String frontend = frontendBaseUrl();
        PreferenciaCreada preferencia = pasarelaPagos.crearPreferencia(new NuevaPreferencia(
                reservaId.toString(),
                "Turno Cancha: " + reserva.getCancha().getNombre() + " (" + reserva.getFecha() + ")",
                reserva.getPrecioTotal(),
                reservaId.toString(),
                frontend + "/reservas/mis-reservas?status=success",
                frontend + "/pagos/checkout/" + reservaId + "?status=failure",
                frontend + "/reservas/mis-reservas?status=pending",
                vence.atZone(ZoneId.systemDefault()).toOffsetDateTime()));

        // Registrar o actualizar el Pago pendiente del turno (no el de los consumos, que con Point
        // también puede quedar pendiente sobre la misma reserva)
        Pago pago = pagoRepository.findByReservaIdAndEstadoAndConcepto(reservaId, EstadoPago.PENDIENTE, ConceptoPago.TURNO)
                .orElse(Pago.builder()
                        .reserva(reserva)
                        .usuario(reserva.getUsuario())
                        .monto(reserva.getPrecioTotal())
                        .metodo(MetodoPago.MERCADOPAGO)
                        .estado(EstadoPago.PENDIENTE)
                        .concepto(ConceptoPago.TURNO)
                        .build());
        pago.setMpPreferenceId(preferencia.id());
        pagoRepository.save(pago);

        log.info("Preferencia {} creada para la reserva ID {}", preferencia.id(), reservaId);
        return new PreferenciaResponse(preferencia.id(), preferencia.urlCheckout());
    }

    private String frontendBaseUrl() {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return "http://localhost:4200";
        }
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    /**
     * El estado del pago se consulta siempre a MercadoPago (nunca se confía en el cuerpo del webhook) y,
     * antes de confirmar el turno, se verifica que el monto y la moneda sean los esperados.
     */
    @Override
    public void procesarWebhook(String type, String paymentId) {
        if (!"payment".equalsIgnoreCase(type)) {
            log.debug("Webhook de MercadoPago de tipo {} ignorado", type);
            return;
        }
        Optional<Pago> existente = pagoRepository.findByMpPaymentId(paymentId);
        if (existente.isPresent() && existente.get().getEstado() == EstadoPago.APROBADO) {
            log.info("Pago MP {} ya procesado como APROBADO (notificación repetida)", paymentId);
            return;
        }

        PagoExterno externo = pasarelaPagos.consultarPago(paymentId);
        String referencia = externo.referenciaExterna();
        if (referencia != null && referencia.startsWith(PREFIJO_CONSUMOS)) {
            procesarCobroDeConsumos(referencia, paymentId, externo);
            return;
        }
        // Un pago que no salió de una preferencia de esta app (sin reservaId) no se puede imputar.
        if (referencia == null || !referencia.matches("\\d+")) {
            log.warn("Pago MP {} sin referencia de reserva: se ignora la notificación", paymentId);
            return;
        }
        Long reservaId = Long.parseLong(referencia);
        Reserva reserva = reservaRepository.findByIdForUpdate(reservaId).orElse(null);
        if (reserva == null) {
            log.warn("Pago MP {} referencia la reserva {} que no existe: se ignora", paymentId, reservaId);
            return;
        }

        // El mismo pago MP reutiliza su registro; si no, se toma el pendiente del turno que todavía no tiene pago MP.
        Pago pago = existente.orElseGet(() -> pagoRepository.findByReservaId(reservaId).stream()
                .filter(p -> p.getEstado() == EstadoPago.PENDIENTE && p.getMpPaymentId() == null
                        && p.getConcepto() == ConceptoPago.TURNO)
                .findFirst()
                .orElseGet(() -> Pago.builder()
                        .reserva(reserva)
                        .usuario(reserva.getUsuario())
                        .monto(reserva.getPrecioTotal())
                        .metodo(MetodoPago.MERCADOPAGO)
                        .estado(EstadoPago.PENDIENTE)
                        .concepto(ConceptoPago.TURNO)
                        .build()));
        pago.setMpPaymentId(paymentId);

        if (externo.aprobado()) {
            if (!montoYMonedaCoinciden(externo, reserva.getPrecioTotal())) {
                pagoRepository.save(pago);
                avisarPagoEnRevision(pago, externo);
                return;
            }
            pago.setEstado(EstadoPago.APROBADO);
            Pago aprobado = pagoRepository.save(pago);
            prepararParaEmail(aprobado);
            emailService.enviarComprobantePago(aprobado);
            if (reserva.getEstadoReserva() == EstadoReserva.PENDIENTE_PAGO || reserva.getEstadoReserva() == EstadoReserva.CONFIRMADA) {
                reservaService.confirmarReserva(reservaId);
            } else {
                // Pago aprobado para una reserva que ya venció o se canceló: se devuelve completo y se le avisa.
                log.warn("Pago MP {} aprobado para la reserva ID {} en estado {}: se encola el reintegro",
                        paymentId, reservaId, reserva.getEstadoReserva());
                reintegroService.reintegrarPagoTardio(aprobado);
            }
        } else if (externo.rechazadoOCancelado()) {
            pago.setEstado(EstadoPago.RECHAZADO);
            pagoRepository.save(pago);
            log.info("Pago MP {} rechazado/cancelado para la reserva ID {}", paymentId, reservaId);
        } else {
            pago.setEstado(EstadoPago.PENDIENTE);
            pagoRepository.save(pago);
        }
    }

    private void procesarCobroDeConsumos(String referencia, String paymentId, PagoExterno externo) {
        Long pagoId;
        try {
            pagoId = Long.parseLong(referencia.substring(PREFIJO_CONSUMOS.length()));
        } catch (NumberFormatException e) {
            log.warn("Referencia de consumos inválida en el pago MP {}", paymentId);
            return;
        }
        Pago pago = pagoRepository.findByIdForUpdate(pagoId)
                .filter(p -> p.getConcepto() == ConceptoPago.CONSUMOS)
                .orElse(null);
        if (pago == null || pago.getEstado() == EstadoPago.APROBADO) {
            return;
        }
        pago.setMpPaymentId(paymentId);
        if (externo.aprobado()) {
            if (!montoYMonedaCoinciden(externo, pago.getMonto())) {
                pagoRepository.save(pago);
                avisarPagoEnRevision(pago, externo);
                return;
            }
            pago.setEstado(EstadoPago.APROBADO);
            pagoRepository.save(pago);
            consumoService.marcarConsumosComoPagados(pago.getId());
        } else if (externo.rechazadoOCancelado()) {
            pago.setEstado(EstadoPago.RECHAZADO);
            pagoRepository.save(pago);
            // Los consumos vuelven a quedar libres para cobrarse de otra forma.
            consumoService.liberarConsumosDelPago(pago.getId());
        }
    }

    private boolean montoYMonedaCoinciden(PagoExterno externo, BigDecimal esperado) {
        return externo.monto() != null && esperado != null
                && externo.monto().compareTo(esperado) >= 0
                && MONEDA.equalsIgnoreCase(externo.moneda());
    }

    private void avisarPagoEnRevision(Pago pago, PagoExterno externo) {
        log.error("Pago MP {} aprobado por {} {} pero se esperaban {} {}: queda en revisión",
                externo.id(), externo.monto(), externo.moneda(), pago.getMonto(), MONEDA);
        String mensaje = String.format("MercadoPago informó el pago %s por %s %s, distinto de lo esperado (%s). Revisalo antes de confirmar el turno.",
                externo.id(), externo.monto(), externo.moneda(), MonedaUtil.formatear(pago.getMonto()));
        usuarioRepository.findByRol(RolUsuario.ADMIN).stream()
                .filter(Usuario::isActivo)
                .forEach(admin -> notificacionInAppService.crear(admin, TipoNotificacion.PAGO_EN_REVISION,
                        "Pago a revisar", mensaje, pago.getReserva() != null ? pago.getReserva().getId() : null));
    }

    // El comprobante lee usuario, reserva y cancha (LAZY): se inicializan dentro de la transacción.
    private void prepararParaEmail(Pago pago) {
        Hibernate.initialize(pago.getUsuario());
        if (pago.getReserva() != null) {
            Hibernate.initialize(pago.getReserva());
            Hibernate.initialize(pago.getReserva().getCancha());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PagoResponse obtenerPorId(Long id, String usuarioEmail) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado con ID: " + id));
        controlAcceso.exigirDuenoOStaff(buscarUsuario(usuarioEmail), pago.getUsuario(), "Pago");
        return pagoMapper.toResponse(pago);
    }

    /**
     * Cobro del turno en el mostrador (efectivo o tarjeta): queda un pago APROBADO a nombre del dueño de la
     * reserva y atendido por quien lo registra. Una reserva PENDIENTE_PAGO se confirma; una ya CONFIRMADA o
     * COMPLETADA (recepción la confirmó para cobrar al llegar) solo suma el pago.
     */
    @Override
    public PagoResponse registrarCobroMostrador(Long reservaId, MetodoPago metodo, String staffEmail) {
        if (metodo == null || !metodo.esCobroEnMostrador()) {
            throw new DatosInvalidosException("En el mostrador el turno se cobra en efectivo o con tarjeta");
        }
        // Bloqueo de la reserva: un doble click registraba dos cobros del mismo turno.
        Reserva reserva = reservaRepository.findByIdForUpdate(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservaId));

        EstadoReserva estado = reserva.getEstadoReserva();
        if (estado != EstadoReserva.PENDIENTE_PAGO && estado != EstadoReserva.CONFIRMADA && estado != EstadoReserva.COMPLETADA) {
            throw new ReservaNoModificableException("No se puede cobrar una reserva en estado " + estado);
        }
        // Un turno pagado online, con bono o ya cobrado en el club no se cobra dos veces.
        if (pagoRepository.existsByReservaIdAndEstadoAndConcepto(reservaId, EstadoPago.APROBADO, ConceptoPago.TURNO)) {
            throw new EstadoInvalidoException("El turno ya tiene un cobro registrado");
        }

        Usuario staff = buscarUsuario(staffEmail);
        Pago saved = pagoRepository.save(Pago.builder()
                .reserva(reserva)
                .usuario(reserva.getUsuario())
                .monto(reserva.getPrecioTotal())
                .metodo(metodo)
                .estado(EstadoPago.APROBADO)
                .concepto(ConceptoPago.TURNO)
                .atendidoPor(staff)
                .build());

        if (estado == EstadoReserva.PENDIENTE_PAGO) {
            // Confirma, libera el lock del horario y avisa al jugador.
            reservaService.confirmarReserva(reservaId);
        }
        log.info("Cobro en mostrador registrado: pago ID {} por {} en {}", saved.getId(), saved.getMonto(), metodo);
        return pagoMapper.toResponse(saved);
    }

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.findByEmail(EmailUtil.normalizar(email))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }
}
