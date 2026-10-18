export type MetodoPago = 'MERCADOPAGO' | 'BONO' | 'EFECTIVO' | 'MERCADOPAGO_POINT' | 'TARJETA';
export type EstadoPago = 'PENDIENTE' | 'APROBADO' | 'RECHAZADO';
/** Qué se cobró: el turno, los consumos de la cuenta o la venta de un bono. */
export type ConceptoPago = 'TURNO' | 'CONSUMOS' | 'BONO';
/** Lo que se cobra en el mostrador y queda aprobado en el acto. */
export type MetodoMostrador = 'EFECTIVO' | 'TARJETA';

export interface CobroMostradorRequest {
  metodo: MetodoMostrador;
}

export interface PreferenciaRequest {
  reservaId: number;
}

export interface PreferenciaResponse {
  preferenceId: string;
  initPoint: string;
}

export interface PagoResponse {
  id: number;
  /** Null en la venta de un bono (el pago apunta al bono). */
  reservaId?: number | null;
  bonoId?: number | null;
  concepto: ConceptoPago;
  usuarioId: number;
  usuarioEmail: string;
  monto: number;
  metodo: MetodoPago;
  estado: EstadoPago;
  mpPreferenceId: string | null;
  mpPaymentId: string | null;
  createdAt: string;
  updatedAt: string | null;
}
