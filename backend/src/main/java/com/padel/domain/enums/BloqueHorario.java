package com.padel.domain.enums;

import java.time.LocalTime;

/**
 * Bloques en los que un jugador declara su disponibilidad semanal. Un turno pertenece al
 * bloque en el que empieza: mañana hasta las 12, tarde hasta las 18, noche desde las 18.
 */
public enum BloqueHorario {
    MANANA,
    TARDE,
    NOCHE;

    public static BloqueHorario de(LocalTime hora) {
        if (hora.isBefore(LocalTime.NOON)) {
            return MANANA;
        }
        return hora.isBefore(LocalTime.of(18, 0)) ? TARDE : NOCHE;
    }
}
