package com.padel.domain.enums;

/**
 * Ciclo de vida de una reserva. Las transiciones válidas viven acá (patrón State): antes cada servicio
 * chequeaba a mano con {@code if (estado != ...)} y era fácil olvidar un caso.
 */
public enum EstadoReserva {
    PENDIENTE_PAGO,
    CONFIRMADA,
    CANCELADA,
    COMPLETADA,
    NO_SHOW;

    public boolean puedePasarA(EstadoReserva destino) {
        return switch (this) {
            case PENDIENTE_PAGO -> destino == CONFIRMADA || destino == CANCELADA;
            case CONFIRMADA -> destino == COMPLETADA || destino == CANCELADA || destino == NO_SHOW;
            case CANCELADA, COMPLETADA, NO_SHOW -> false;
        };
    }

    /** Estados en los que la reserva ocupa la cancha (retenida mientras se paga, o ya tomada). */
    public boolean ocupaLaCancha() {
        return this == PENDIENTE_PAGO || this == CONFIRMADA || this == COMPLETADA;
    }
}
