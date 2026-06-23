import { MetodoPago } from './pago.model';

export type EstadoConsumoPago = 'PENDIENTE' | 'PAGADO';

export interface ConsumoRequest {
  productoId: number;
  cantidad: number;
}

export interface CerrarCuentaRequest {
  metodo: MetodoPago;
}

export interface ConsumoResponse {
  id: number;
  reservaId: number;
  usuarioId: number;
  usuarioEmail: string;
  productoId: number;
  productoNombre: string;
  pagoId: number | null;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
  estadoPago: EstadoConsumoPago;
  createdAt: string;
}

export interface MetodoTotal {
  metodo: string;
  total: number;
}

export interface CajaDiariaResponse {
  detalles: MetodoTotal[];
}
