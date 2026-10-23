package com.padel.service.impl;

import com.padel.domain.entity.Reserva;
import com.padel.service.NotificacionService;
import com.padel.util.MonedaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Avisos por WhatsApp de las reservas. Arma el texto y lo deja en la bandeja de salida (se envía con
 * reintentos, ver DespachadorMensajes). El log ya no guarda el teléfono ni el contenido del mensaje.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppNotificacionServiceImpl implements NotificacionService {

    private final BandejaSalida bandejaSalida;

    @Override
    public void enviarConfirmacion(Reserva reserva) {
        encolar(reserva, String.format(
                "¡Hola %s! Tu reserva para la cancha %s el día %s de %s a %s ha sido CONFIRMADA. Total: %s.",
                reserva.getUsuario().getNombre(),
                reserva.getCancha().getNombre(),
                reserva.getFecha(),
                hora(reserva.getHoraInicio()),
                hora(reserva.getHoraFin()),
                MonedaUtil.formatear(reserva.getPrecioTotal())));
    }

    @Override
    public void enviarRecordatorio(Reserva reserva) {
        encolar(reserva, String.format(
                "¡Hola %s! Te recordamos tu turno de hoy en la cancha %s de %s a %s. ¡Te esperamos!",
                reserva.getUsuario().getNombre(),
                reserva.getCancha().getNombre(),
                hora(reserva.getHoraInicio()),
                hora(reserva.getHoraFin())));
    }

    @Override
    public void enviarCancelacion(Reserva reserva) {
        String motivo = reserva.getMotivoCancelacion() != null ? reserva.getMotivoCancelacion() : "Cancelada por el usuario";
        encolar(reserva, String.format(
                "¡Hola %s! Tu reserva para la cancha %s el día %s ha sido CANCELADA. Motivo: %s.",
                reserva.getUsuario().getNombre(),
                reserva.getCancha().getNombre(),
                reserva.getFecha(),
                motivo));
    }

    private void encolar(Reserva reserva, String texto) {
        String telefono = reserva.getUsuario().getTelefono();
        if (telefono == null || telefono.isBlank()) {
            return; // sin teléfono no hay WhatsApp; el aviso in-app sigue llegando
        }
        bandejaSalida.encolarWhatsApp(telefono.trim(), texto);
    }

    private static String hora(java.time.LocalTime hora) {
        return hora.toString().substring(0, 5);
    }
}
