package com.padel.domain.evento;

import com.padel.domain.entity.Reserva;

/**
 * Se publica dentro de la transacción que cancela la reserva. {@code detalleReintegro} es el texto que
 * explica al jugador qué pasa con lo que pagó (vacío si no había nada cobrado).
 */
public record ReservaCanceladaEvent(Reserva reserva, OrigenCancelacion origen, String detalleReintegro) {
}
