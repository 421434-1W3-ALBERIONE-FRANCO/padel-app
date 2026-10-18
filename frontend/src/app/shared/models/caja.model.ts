import { EstadoReserva } from './reserva.model';
import { ConceptoPago, MetodoPago } from './pago.model';

export interface MetodoTotal {
  metodo: MetodoPago;
  total: number;
  cantidad: number;
  /** false para BONO: un turno pagado con horas de bono no es plata nueva (entró al vender el bono). */
  ingreso: boolean;
}

export interface MovimientoCaja {
  pagoId: number;
  fechaHora: string;
  concepto: ConceptoPago;
  detalle: string;
  cliente: string;
  metodo: MetodoPago;
  monto: number;
  /** Staff que registró el cobro; no viene en los pagos online. */
  atendidoPor?: string | null;
  ingreso: boolean;
}

/** Turno del día confirmado sin cobro registrado (se confirmó para pagar al llegar). */
export interface PendienteCobro {
  reservaId: number;
  canchaNombre: string;
  horaInicio: string;
  horaFin: string;
  cliente: string;
  monto: number;
  estadoReserva: EstadoReserva;
}

export interface CajaDiariaResponse {
  fecha: string;
  totalIngresos: number;
  /** Lo que tiene que haber en el cajón para el arqueo. */
  totalEfectivo: number;
  detalles: MetodoTotal[];
  movimientos: MovimientoCaja[];
  pendientesDeCobro: PendienteCobro[];
}
