package com.padel.domain.enums;

public enum MetodoPago {
    MERCADOPAGO,
    BONO,
    EFECTIVO,
    MERCADOPAGO_POINT,
    TARJETA;

    /** Cobro que el staff registra en el mostrador y queda aprobado en el acto. */
    public boolean esCobroEnMostrador() {
        return this == EFECTIVO || this == TARJETA;
    }

    /**
     * Si entra plata a la caja. Un turno pagado con BONO no es ingreso: el dinero entró al vender el bono.
     */
    public boolean esIngreso() {
        return this != BONO;
    }
}
