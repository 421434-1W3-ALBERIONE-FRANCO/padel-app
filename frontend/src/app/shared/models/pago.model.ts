export type MetodoPago = 'MERCADOPAGO' | 'BONO' | 'EFECTIVO';
export type EstadoPago = 'PENDIENTE' | 'APROBADO' | 'RECHAZADO';

export interface PreferenciaRequest {
  reservaId: number;
}

export interface PreferenciaResponse {
  preferenceId: string;
  initPoint: string;
}

export interface PagoResponse {
  id: number;
  reservaId: number;
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
