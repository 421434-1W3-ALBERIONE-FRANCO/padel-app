package com.padel.service.impl;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.MetodoPago;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.dto.response.PagoResponse;
import com.padel.dto.response.PreferenciaResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.exception.ReservaNoModificableException;
import com.padel.mapper.PagoMapper;
import com.padel.repository.PagoRepository;
import com.padel.repository.ReservaRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.EmailService;
import com.padel.service.NotificacionInAppService;
import com.padel.service.PagoService;
import com.padel.service.ReservaService;
import com.padel.service.ConsumoService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PagoServiceImpl implements PagoService {

    private final PagoRepository pagoRepository;
    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReservaService reservaService;
    private final ConsumoService consumoService;
    private final PagoMapper pagoMapper;
    private final EmailService emailService;
    private final NotificacionInAppService notificacionInAppService;

    @Value("${mercadopago.access-token}")
    private String accessToken;

    // URL pública del frontend (Netlify en prod): MercadoPago redirige ahí al terminar el checkout.
    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @PostConstruct
    public void init() {
        log.info("Inicializando SDK de MercadoPago con token de acceso");
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    @Override
    public PreferenciaResponse crearPreferencia(Long reservaId, String usuarioEmail) {
        log.info("Creando preferencia de MercadoPago para reserva ID {} e usuario {}", reservaId, usuarioEmail);

        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservaId));

        Usuario usuario = usuarioRepository.findByEmail(usuarioEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + usuarioEmail));

        // Validar permisos
        boolean isAdminOrRecepcion = usuario.getRol() == RolUsuario.ADMIN || usuario.getRol() == RolUsuario.RECEPCIONISTA;
        if (!isAdminOrRecepcion && !reserva.getUsuario().getId().equals(usuario.getId())) {
            throw new ResourceNotFoundException("Reserva no encontrada para el usuario especificado");
        }

        // Sólo se puede generar un cobro para una reserva que sigue esperando pago
        if (reserva.getEstadoReserva() != EstadoReserva.PENDIENTE_PAGO) {
            throw new ReservaNoModificableException("No se puede generar un cobro para una reserva en estado " + reserva.getEstadoReserva());
        }

        try {
            // Configurar el ítem
            PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
                    .id(reservaId.toString())
                    .title("Turno Cancha: " + reserva.getCancha().getNombre() + " (" + reserva.getFecha() + ")")
                    .quantity(1)
                    .currencyId("ARS")
                    .unitPrice(reserva.getPrecioTotal())
                    .build();

            // Configurar URLs de retorno (antes fijas a localhost: en prod MP devolvía al usuario a su propia PC)
            String frontend = frontendBaseUrl();
            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(frontend + "/reservas/mis-reservas?status=success")
                    .failure(frontend + "/pagos/checkout/" + reservaId + "?status=failure")
                    .pending(frontend + "/reservas/mis-reservas?status=pending")
                    .build();

            // Construir la preferencia de pago
            PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                    .items(Collections.singletonList(itemRequest))
                    .backUrls(backUrls)
                    .externalReference(reservaId.toString())
                    .autoReturn("approved")
                    .build();

            PreferenceClient client = new PreferenceClient();
            Preference preference = client.create(preferenceRequest);

            // Registrar o actualizar el Pago pendiente
            Pago pago = pagoRepository.findByReservaIdAndEstado(reservaId, EstadoPago.PENDIENTE)
                    .orElse(Pago.builder()
                            .reserva(reserva)
                            .usuario(reserva.getUsuario())
                            .monto(reserva.getPrecioTotal())
                            .metodo(MetodoPago.MERCADOPAGO)
                            .estado(EstadoPago.PENDIENTE)
                            .build());

            pago.setMpPreferenceId(preference.getId());
            pagoRepository.save(pago);

            log.info("Preferencia creada exitosamente con ID {}", preference.getId());
            return new PreferenciaResponse(preference.getId(), preference.getInitPoint());
        } catch (Exception e) {
            log.error("Error al crear preferencia de MercadoPago: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar la pasarela de pago", e);
        }
    }

    private String frontendBaseUrl() {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return "http://localhost:4200";
        }
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    @Override
    public void procesarWebhook(String type, String paymentId) {
        log.info("Recibido webhook de MercadoPago: tipo={}, paymentId={}", type, paymentId);

        if (!"payment".equalsIgnoreCase(type)) {
            log.info("Ignorando webhook de tipo: {}", type);
            return;
        }

        try {
            // 1. Verificar idempotencia primero
            Optional<Pago> existingPagoOpt = pagoRepository.findByMpPaymentId(paymentId);
            if (existingPagoOpt.isPresent()) {
                Pago existingPago = existingPagoOpt.get();
                if (existingPago.getEstado() == EstadoPago.APROBADO) {
                    log.info("Pago MP ID {} ya procesado previamente en estado APROBADO. Idempotente.", paymentId);
                    return;
                }
            }

            PaymentClient client = new PaymentClient();
            Payment payment = client.get(Long.parseLong(paymentId));
            String status = payment.getStatus();
            String externalReference = payment.getExternalReference();

            // Un pago que no salió de una preferencia de esta app (sin reservaId) no se puede imputar.
            // Antes reventaba con NumberFormatException -> 500 -> MercadoPago reintentaba el webhook sin fin.
            if (externalReference == null || !externalReference.matches("\\d+")) {
                log.warn("Pago MP {} sin external_reference de reserva ({}). Se ignora la notificación.", paymentId, externalReference);
                return;
            }
            Long reservaId = Long.parseLong(externalReference);

            log.info("Detalles del pago MP: status={}, externalReference (reservaId)={}",
                    status, reservaId);

            // 2. El mismo pago MP (ej. primero in_process y luego approved) reutiliza su registro; si no,
            //    se toma el pendiente de la reserva que todavía no tiene pago MP asociado (mp_payment_id es único).
            Pago pago = existingPagoOpt.orElseGet(() -> pagoRepository.findByReservaId(reservaId).stream()
                    .filter(p -> p.getEstado() == EstadoPago.PENDIENTE && p.getMpPaymentId() == null)
                    .findFirst()
                    .orElseGet(() -> {
                        Reserva reserva = reservaRepository.findById(reservaId)
                                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservaId));
                        return Pago.builder()
                                .reserva(reserva)
                                .usuario(reserva.getUsuario())
                                .monto(payment.getTransactionAmount() != null ? payment.getTransactionAmount() : reserva.getPrecioTotal())
                                .metodo(MetodoPago.MERCADOPAGO)
                                .estado(EstadoPago.PENDIENTE)
                                .build();
                    }));

            pago.setMpPaymentId(paymentId);

            if ("approved".equalsIgnoreCase(status)) {
                pago.setEstado(EstadoPago.APROBADO);
                Pago saved = pagoRepository.save(pago);
                // El email es @Async: se inicializan acá los datos lazy que usa (usuario, reserva, cancha)
                // para que el otro hilo no dependa de esta sesión de Hibernate.
                Hibernate.initialize(saved.getUsuario());
                if (saved.getReserva() != null) {
                    Hibernate.initialize(saved.getReserva());
                    Hibernate.initialize(saved.getReserva().getCancha());
                }
                emailService.enviarComprobantePago(saved);
                if (saved.getMetodo() == MetodoPago.MERCADOPAGO_POINT) {
                    log.info("Pago presencial aprobado en DB. Procediendo a pagar consumos vinculados al Pago ID {}", saved.getId());
                    consumoService.marcarConsumosComoPagados(saved.getId());
                } else if (saved.getReserva().getEstadoReserva() == EstadoReserva.PENDIENTE_PAGO
                        || saved.getReserva().getEstadoReserva() == EstadoReserva.CONFIRMADA) {
                    log.info("Pago aprobado registrado en DB. Procediendo a confirmar reserva ID {}", reservaId);
                    reservaService.confirmarReserva(reservaId);
                } else {
                    // Pago aprobado que llegó tarde (la reserva ya venció o se canceló). Antes confirmarReserva
                    // lanzaba excepción, se hacía rollback del pago y MP reintentaba sin fin: el cobro quedaba
                    // sin registrar. Ahora queda APROBADO para que el club lo reintegre o reubique el turno.
                    log.warn("Pago MP {} aprobado para la reserva ID {} en estado {}: queda registrado para reintegro manual.",
                            paymentId, reservaId, saved.getReserva().getEstadoReserva());
                    notificacionInAppService.crear(
                            saved.getUsuario(),
                            TipoNotificacion.RESERVA_CANCELADA_FALTA_PAGO,
                            "Pago recibido para una reserva vencida",
                            String.format("Recibimos tu pago de $%s, pero la reserva del %s ya no estaba vigente. "
                                    + "Contactá al club para reprogramar el turno o pedir el reintegro.",
                                    saved.getMonto(), saved.getReserva().getFecha()),
                            reservaId
                    );
                }
            } else if ("rejected".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)) {
                pago.setEstado(EstadoPago.RECHAZADO);
                pagoRepository.save(pago);
                log.info("Pago rechazado/cancelado registrado para reserva ID {}", reservaId);
            } else {
                pago.setEstado(EstadoPago.PENDIENTE);
                pagoRepository.save(pago);
                log.info("Pago en estado pendiente registrado para reserva ID {}", reservaId);
            }
        } catch (Exception e) {
            log.error("Error al procesar webhook de MercadoPago: {}", e.getMessage(), e);
            throw new RuntimeException("Error en procesamiento de webhook", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PagoResponse obtenerPorId(Long id, String usuarioEmail) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado con ID: " + id));

        Usuario usuario = usuarioRepository.findByEmail(usuarioEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + usuarioEmail));

        // Validar permisos
        boolean isAdminOrRecepcion = usuario.getRol() == RolUsuario.ADMIN || usuario.getRol() == RolUsuario.RECEPCIONISTA;
        if (!isAdminOrRecepcion && !pago.getUsuario().getId().equals(usuario.getId())) {
            throw new ResourceNotFoundException("Pago no encontrado para el usuario especificado");
        }

        return pagoMapper.toResponse(pago);
    }
}
