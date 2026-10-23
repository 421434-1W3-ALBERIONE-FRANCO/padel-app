package com.padel.domain.evento;

import com.padel.domain.entity.Reserva;

/** Se publica dentro de la transacción que confirma la reserva (los avisos se encolan en la misma transacción). */
public record ReservaConfirmadaEvent(Reserva reserva) {
}
