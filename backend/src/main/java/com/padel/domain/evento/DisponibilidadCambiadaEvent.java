package com.padel.domain.evento;

/**
 * La grilla de una cancha cambió (reserva creada, confirmada o cancelada; bloqueo o franja modificados).
 * Se difunde por WebSocket recién después del commit, para que los clientes no relean datos sin confirmar.
 */
public record DisponibilidadCambiadaEvent(Long canchaId) {
}
