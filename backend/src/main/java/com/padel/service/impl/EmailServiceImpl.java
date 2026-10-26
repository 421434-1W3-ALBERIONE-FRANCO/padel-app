package com.padel.service.impl;

import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Usuario;
import com.padel.service.EmailService;
import com.padel.util.MonedaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Arma los emails con la plantilla Thymeleaf (contenido escapado) y los deja en la bandeja de salida, que
 * los envía con reintentos fuera de la transacción.
 */
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final String PLANTILLA = "email/mensaje";
    private static final Locale ESPANOL = Locale.forLanguageTag("es-AR");

    private final BandejaSalida bandejaSalida;
    private final ITemplateEngine plantillas;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Override
    public void enviarBienvenida(Usuario usuario) {
        encolar(usuario, "¡Bienvenido a PadelApp!", "¡Bienvenido a PadelApp!",
                List.of("Tu cuenta está lista. Ya podés reservar tu cancha, comprar bonos de horas y anotarte a torneos desde la app."),
                "Ir a PadelApp", base(), null);
    }

    @Override
    public void enviarComprobantePago(Pago pago) {
        String detalle = pago.getReserva() != null
                ? String.format("Reserva: cancha %s el %s.", pago.getReserva().getCancha().getNombre(), pago.getReserva().getFecha())
                : "";
        encolar(pago.getUsuario(), "Comprobante de pago - PadelApp", "Pago aprobado",
                List.of(String.format("Registramos tu pago por %s (método: %s).", MonedaUtil.formatear(pago.getMonto()), pago.getMetodo()), detalle),
                null, null, null);
    }

    @Override
    public void enviarConfirmacionCambioPassword(Usuario usuario) {
        encolar(usuario, "Tu contraseña fue actualizada - PadelApp", "Contraseña actualizada",
                List.of("Tu contraseña fue cambiada y se cerraron las sesiones abiertas en otros dispositivos."),
                null, null, "Si no fuiste vos quien hizo este cambio, contactate con el club de inmediato.");
    }

    @Override
    public void enviarRecuperacionPassword(Usuario usuario, String tokenPlano) {
        encolar(usuario, "Recuperación de contraseña - PadelApp", "Recuperá tu contraseña",
                List.of("Recibimos un pedido para elegir una nueva contraseña. El enlace vale por 1 hora y se puede usar una sola vez."),
                "Elegir nueva contraseña", base() + "/auth/resetear-password?token=" + codificar(tokenPlano),
                "Si no lo pediste, ignorá este correo: tu contraseña no cambia.");
    }

    @Override
    public void enviarVerificacionEmail(Usuario usuario, String tokenPlano) {
        encolar(usuario, "Confirmá tu email - PadelApp", "Confirmá tu email",
                List.of("Para activar tu cuenta confirmá que este email es tuyo. El enlace vale por 24 horas."),
                "Confirmar email", base() + "/auth/verificar-email?token=" + codificar(tokenPlano),
                "Si no creaste una cuenta en PadelApp, ignorá este correo.");
    }

    private void encolar(Usuario usuario, String asunto, String titulo, List<String> parrafos,
                         String botonTexto, String botonUrl, String nota) {
        Context contexto = new Context(ESPANOL);
        contexto.setVariable("titulo", titulo);
        contexto.setVariable("saludo", "Hola " + usuario.getNombre() + ",");
        contexto.setVariable("parrafos", parrafos.stream().filter(p -> p != null && !p.isBlank()).toList());
        contexto.setVariable("botonTexto", botonTexto);
        contexto.setVariable("botonUrl", botonUrl);
        contexto.setVariable("nota", nota);
        bandejaSalida.encolarEmail(usuario.getEmail(), asunto, plantillas.process(PLANTILLA, contexto));
    }

    private String base() {
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
