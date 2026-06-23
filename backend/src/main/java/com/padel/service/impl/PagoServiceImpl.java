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
import com.padel.domain.enums.MetodoPago;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.response.PagoResponse;
import com.padel.dto.response.PreferenciaResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.PagoMapper;
import com.padel.repository.PagoRepository;
import com.padel.repository.ReservaRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.PagoService;
import com.padel.service.ReservaService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final PagoMapper pagoMapper;

    @Value("${mercadopago.access-token}")
    private String accessToken;

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

        try {
            // Configurar el ítem
            PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
                    .id(reservaId.toString())
                    .title("Turno Cancha: " + reserva.getCancha().getNombre() + " (" + reserva.getFecha() + ")")
                    .quantity(1)
                    .currencyId("ARS")
                    .unitPrice(reserva.getPrecioTotal())
                    .build();

            // Configurar URLs de retorno
            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success("http://localhost:4200/reservas/mis-reservas?status=success")
                    .failure("http://localhost:4200/pagos/checkout/" + reservaId + "?status=failure")
                    .pending("http://localhost:4200/reservas/mis-reservas?status=pending")
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
            Long reservaId = Long.parseLong(payment.getExternalReference());

            log.info("Detalles del pago MP: status={}, externalReference (reservaId)={}", 
                    status, reservaId);

            // 2. Buscar por reserva
            Pago pago = pagoRepository.findByReservaId(reservaId).stream()
                    .filter(p -> p.getEstado() == EstadoPago.PENDIENTE)
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
                    });

            pago.setMpPaymentId(paymentId);

            if ("approved".equalsIgnoreCase(status)) {
                pago.setEstado(EstadoPago.APROBADO);
                pagoRepository.save(pago);
                log.info("Pago aprobado registrado en DB. Procediendo a confirmar reserva ID {}", reservaId);
                reservaService.confirmarReserva(reservaId);
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
