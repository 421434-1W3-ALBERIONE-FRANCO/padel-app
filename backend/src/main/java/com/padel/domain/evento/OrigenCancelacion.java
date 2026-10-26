package com.padel.domain.evento;

/** Quién originó la cancelación de una reserva: define si corresponde reintegro y qué aviso recibe el jugador. */
public enum OrigenCancelacion {
    /** El propio jugador cancela su turno: reintegro solo con la anticipación mínima. */
    JUGADOR,
    /** El club cancela (staff o bloqueo de cancha): siempre corresponde reintegro. */
    CLUB,
    /** Venció el plazo para pagar: no hubo cobro, no hay nada que reintegrar. */
    FALTA_DE_PAGO
}
