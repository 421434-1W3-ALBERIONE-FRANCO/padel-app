package com.padel.service.impl;

import com.padel.domain.entity.Reserva;
import com.padel.service.NotificacionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioWhatsAppServiceImpl implements NotificacionService {

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.whatsapp-from:whatsapp:+14155238886}")
    private String whatsappFrom;

    @Value("${twilio.whatsapp-to-prefix:whatsapp:}")
    private String whatsappToPrefix;

    @PostConstruct
    public void init() {
        if (accountSid != null && !accountSid.trim().isEmpty() &&
            authToken != null && !authToken.trim().isEmpty()) {
            try {
                com.twilio.Twilio.init(accountSid, authToken);
                log.info("Twilio inicializado exitosamente.");
            } catch (Exception e) {
                log.error("Error al inicializar Twilio: {}", e.getMessage());
            }
        } else {
            log.warn("Twilio no inicializado. Faltan credenciales (twilio.account-sid / twilio.auth-token).");
        }
    }

    @Override
    @Async("notificacionExecutor")
    public void enviarConfirmacion(Reserva reserva) {
        String telefono = reserva.getUsuario().getTelefono();
        if (telefono == null || telefono.trim().isEmpty()) {
            log.warn("El usuario {} no tiene un teléfono configurado para notificaciones.", reserva.getUsuario().getEmail());
            return;
        }

        String mensajeText = String.format(
                "¡Hola %s! Tu reserva para la cancha %s el día %s de %s a %s ha sido CONFIRMADA. Total: $%s.",
                reserva.getUsuario().getNombre(),
                reserva.getCancha().getNombre(),
                reserva.getFecha().toString(),
                reserva.getHoraInicio().toString().substring(0, 5),
                reserva.getHoraFin().toString().substring(0, 5),
                reserva.getPrecioTotal().toString()
        );

        enviarWhatsApp(telefono, mensajeText);
    }

    @Override
    @Async("notificacionExecutor")
    public void enviarRecordatorio(Reserva reserva) {
        String telefono = reserva.getUsuario().getTelefono();
        if (telefono == null || telefono.trim().isEmpty()) {
            return;
        }

        String mensajeText = String.format(
                "¡Hola %s! Te recordamos tu turno de hoy en la cancha %s de %s a %s. ¡Te esperamos!",
                reserva.getUsuario().getNombre(),
                reserva.getCancha().getNombre(),
                reserva.getHoraInicio().toString().substring(0, 5),
                reserva.getHoraFin().toString().substring(0, 5)
        );

        enviarWhatsApp(telefono, mensajeText);
    }

    @Override
    @Async("notificacionExecutor")
    public void enviarCancelacion(Reserva reserva) {
        String telefono = reserva.getUsuario().getTelefono();
        if (telefono == null || telefono.trim().isEmpty()) {
            return;
        }

        String motivo = reserva.getMotivoCancelacion() != null ? reserva.getMotivoCancelacion() : "Cancelada por el usuario";
        String mensajeText = String.format(
                "¡Hola %s! Tu reserva para la cancha %s el día %s ha sido CANCELADA. Motivo: %s.",
                reserva.getUsuario().getNombre(),
                reserva.getCancha().getNombre(),
                reserva.getFecha().toString(),
                motivo
        );

        enviarWhatsApp(telefono, mensajeText);
    }

    private void enviarWhatsApp(String telefonoDestinatario, String mensajeText) {
        String to = whatsappToPrefix + telefonoDestinatario;
        String from = whatsappFrom;

        log.info("Enviando WhatsApp a {} con mensaje: {}", to, mensajeText);

        if (accountSid == null || accountSid.trim().isEmpty() ||
            authToken == null || authToken.trim().isEmpty()) {
            log.warn("Notificación de WhatsApp simulada (Twilio no inicializado).");
            return;
        }

        try {
            com.twilio.rest.api.v2010.account.Message.creator(
                    new com.twilio.type.PhoneNumber(to),
                    new com.twilio.type.PhoneNumber(from),
                    mensajeText
            ).create();
            log.info("Mensaje enviado con éxito via Twilio.");
        } catch (Exception e) {
            log.error("Error al enviar mensaje via Twilio (excepción capturada para no interrumpir el flujo): {}", e.getMessage());
        }
    }
}
