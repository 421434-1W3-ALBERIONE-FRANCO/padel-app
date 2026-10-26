package com.padel.service.listener;

import com.padel.domain.entity.Reserva;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.domain.evento.ReservaCanceladaEvent;
import com.padel.domain.evento.ReservaConfirmadaEvent;
import com.padel.service.NotificacionInAppService;
import com.padel.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Avisos al jugador ante cambios de su reserva. Escucha los eventos de dominio en forma sincrónica, dentro
 * de la misma transacción: los mensajes se encolan (outbox) junto con el cambio de estado y, si la
 * transacción se revierte, no sale ningún aviso de algo que no pasó.
 */
@Component
@RequiredArgsConstructor
public class NotificacionesReservaListener {

    private final NotificacionService notificacionService;
    private final NotificacionInAppService notificacionInAppService;

    @EventListener
    public void alConfirmar(ReservaConfirmadaEvent evento) {
        notificacionService.enviarConfirmacion(evento.reserva());
    }

    @EventListener
    public void alCancelar(ReservaCanceladaEvent evento) {
        Reserva r = evento.reserva();
        String detalle = evento.detalleReintegro() == null ? "" : evento.detalleReintegro();
        switch (evento.origen()) {
            case FALTA_DE_PAGO -> notificacionInAppService.crear(
                    r.getUsuario(),
                    TipoNotificacion.RESERVA_CANCELADA_FALTA_PAGO,
                    "Reserva cancelada por falta de pago",
                    String.format("Tu reserva del %s de %s a %s fue cancelada automáticamente por no completarse el pago a tiempo.",
                            r.getFecha(), r.getHoraInicio(), r.getHoraFin()),
                    r.getId());
            case JUGADOR -> {
                notificacionService.enviarCancelacion(r);
                if (!detalle.isBlank()) {
                    notificacionInAppService.crear(r.getUsuario(), TipoNotificacion.RESERVA_CANCELADA,
                            "Cancelaste tu reserva",
                            String.format("Tu reserva del %s de %s a %s quedó cancelada.%s",
                                    r.getFecha(), r.getHoraInicio(), r.getHoraFin(), detalle),
                            r.getId());
                }
            }
            case CLUB -> {
                notificacionService.enviarCancelacion(r);
                notificacionInAppService.crear(r.getUsuario(), TipoNotificacion.RESERVA_CANCELADA,
                        "El club canceló tu reserva",
                        String.format("Tu reserva del %s de %s a %s fue cancelada. Motivo: %s.%s",
                                r.getFecha(), r.getHoraInicio(), r.getHoraFin(), r.getMotivoCancelacion(), detalle),
                        r.getId());
            }
        }
    }
}
