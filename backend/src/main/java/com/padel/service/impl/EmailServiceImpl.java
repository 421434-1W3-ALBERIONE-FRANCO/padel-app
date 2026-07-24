package com.padel.service.impl;

import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Usuario;
import com.padel.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.from:no-reply@padelapp.com}")
    private String from;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Override
    @Async("emailExecutor")
    public void enviarBienvenida(Usuario usuario) {
        String cuerpo = construirHtml(
                "¡Bienvenido a PadelApp, " + usuario.getNombre() + "!",
                "Tu cuenta fue creada exitosamente. Ya podés reservar tu cancha, comprar bonos de horas y anotarte a torneos desde la app."
        );
        enviarEmail(usuario.getEmail(), "¡Bienvenido a PadelApp!", cuerpo);
    }

    @Override
    @Async("emailExecutor")
    public void enviarComprobantePago(Pago pago) {
        Usuario usuario = pago.getUsuario();
        String detalleReserva = pago.getReserva() != null
                ? String.format("Reserva: cancha %s el %s.",
                        pago.getReserva().getCancha().getNombre(),
                        pago.getReserva().getFecha())
                : "";
        String cuerpo = construirHtml(
                "Pago aprobado",
                String.format(
                        "Hola %s, registramos tu pago por $%s (método: %s).<br>%s",
                        usuario.getNombre(), pago.getMonto(), pago.getMetodo(), detalleReserva
                )
        );
        enviarEmail(usuario.getEmail(), "Comprobante de pago - PadelApp", cuerpo);
    }

    @Override
    @Async("emailExecutor")
    public void enviarConfirmacionCambioPassword(Usuario usuario) {
        String cuerpo = construirHtml(
                "Contraseña actualizada",
                "Hola " + usuario.getNombre() + ", tu contraseña fue cambiada exitosamente. " +
                        "Si no fuiste vos quien hizo este cambio, contactate con el club de inmediato."
        );
        enviarEmail(usuario.getEmail(), "Tu contraseña fue actualizada - PadelApp", cuerpo);
    }

    @Override
    @Async("emailExecutor")
    public void enviarRecuperacionPassword(Usuario usuario, String tokenPlano) {
        String link = frontendUrl + "/auth/resetear-password?token=" + tokenPlano;
        String cuerpo = construirHtml(
                "Recuperá tu contraseña",
                "Hola " + usuario.getNombre() + ", hacé clic en el siguiente enlace para elegir una nueva contraseña " +
                        "(válido por 1 hora): <a href=\"" + link + "\">" + link + "</a><br>" +
                        "Si no solicitaste este cambio, podés ignorar este correo."
        );
        enviarEmail(usuario.getEmail(), "Recuperación de contraseña - PadelApp", cuerpo);
    }

    private void enviarEmail(String destinatario, String asunto, String htmlBody) {
        if (mailHost == null || mailHost.trim().isEmpty()) {
            log.warn("Envío de email simulado (SMTP no configurado). Destinatario: {}, asunto: {}", destinatario, asunto);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email '{}' enviado exitosamente a {}", asunto, destinatario);
        } catch (Exception e) {
            log.error("Error al enviar email a {} (excepción capturada para no interrumpir el flujo): {}", destinatario, e.getMessage());
        }
    }

    private String construirHtml(String titulo, String cuerpo) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 24px; color: #1e293b;">
                    <h2 style="color: #0f172a;">%s</h2>
                    <p style="font-size: 14px; line-height: 1.6;">%s</p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 24px 0;">
                    <p style="font-size: 11px; color: #94a3b8;">PadelApp - Sistema de gestión de canchas</p>
                </div>
                """.formatted(titulo, cuerpo);
    }
}
