export type EstadoReserva = 'PENDIENTE_PAGO' | 'CONFIRMADA' | 'CANCELADA' | 'COMPLETADA' | 'NO_SHOW';
export type OrigenReserva = 'APP' | 'RECEPCION';

export interface ReservaRequest {
  canchaId: number;
  franjaId: number;
  fecha: string;
  origen: OrigenReserva;
}

export interface ReservaResponse {
  id: number;
  usuarioId: number;
  usuarioEmail: string;
  canchaId: number;
  canchaNombre: string;
  franjaId: number;
  fecha: string;
  horaInicio: string;
  horaFin: string;
  precioTotal: number;
  estadoReserva: EstadoReserva;
  origen: OrigenReserva;
  motivoCancelacion: string | null;
  createdAt: string;
  updatedAt: string | null;
}
