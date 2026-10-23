package com.padel.domain.enums;

public enum EstadoInscripcion {
    /** Un jugador invitó a su compañero; la pareja no ocupa cupo hasta que el compañero acepta. */
    PENDIENTE_ACEPTACION,
    CONFIRMADA,
    RECHAZADA,
    CANCELADA;

    public boolean vigente() {
        return this == PENDIENTE_ACEPTACION || this == CONFIRMADA;
    }
}
