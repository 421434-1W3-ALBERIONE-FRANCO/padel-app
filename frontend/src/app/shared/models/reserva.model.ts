export type EstadoReserva = 'PENDIENTE_PAGO' | 'CONFIRMADA' | 'CANCELADA' | 'COMPLETADA' | 'NO_SHOW';
export type OrigenReserva = 'APP' | 'RECEPCION';

export interface ReservaRequest {
  canchaId: number;
  franjaId: number;
  fecha: string;
  origen: OrigenReserva;
  duracionMin?: number;   // opcional: si falta, se usa la duración de la franja (turno estándar)
  usuarioId?: number;     // opcional: alta rápida por staff para un cliente
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
