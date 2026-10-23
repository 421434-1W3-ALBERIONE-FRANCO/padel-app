package com.padel.domain.enums;

/**
 * Qué se cobró: el turno, los consumos de la cuenta o la venta de un bono de horas. REINTEGRO es la
 * devolución de un cobro anterior (monto negativo, apunta al pago original).
 */
public enum ConceptoPago {
    TURNO,
    CONSUMOS,
    BONO,
    REINTEGRO
}
