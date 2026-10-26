package com.padel.integracion.mensajeria;

import com.padel.util.EmailUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Adaptador SMTP de {@link EnviadorEmail}. Sin servidor SMTP configurado el envío se simula (desarrollo):
 * con {@code app.mail.registrar-contenido-simulado=true} el cuerpo va al log para poder seguir los enlaces
 * de verificación o recuperación sin un servidor de correo. Nunca activarlo en producción.
 */
@Component
@Slf4j
public class SmtpEnviadorEmail implements EnviadorEmail {

    private final JavaMailSender mailSender;
    private final String servidor;
    private final String remitente;
    private final boolean registrarContenidoSimulado;

    public SmtpEnviadorEmail(JavaMailSender mailSender,
                             @Value("${spring.mail.host:}") String servidor,
                             @Value("${app.mail.from:no-reply@padelapp.com}") String remitente,
                             @Value("${app.mail.registrar-contenido-simulado:false}") boolean registrarContenidoSimulado) {
        this.mailSender = mailSender;
        this.servidor = servidor;
        this.remitente = remitente;
        this.registrarContenidoSimulado = registrarContenidoSimulado;
    }

    @Override
    public void enviar(String destinatario, String asunto, String html) {
        if (servidor == null || servidor.isBlank()) {
            log.warn("Email simulado (SMTP no configurado) para {}: {}", EmailUtil.enmascarar(destinatario), asunto);
            if (registrarContenidoSimulado) {
                log.info("Contenido del email simulado:\n{}", html);
            }
            return;
        }
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");
            helper.setFrom(remitente);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(html, true);
            mailSender.send(mensaje);
        } catch (MailException | MessagingException e) {
            throw new EnvioFallidoException("No se pudo enviar el email: " + e.getMessage(), e);
        }
    }
}
