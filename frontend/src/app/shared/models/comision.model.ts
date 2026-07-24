export type OrigenComision = 'CONSUMO' | 'BONO';
export type EstadoComision = 'PENDIENTE' | 'PAGADA';

export interface ComisionResponse {
  id: number;
  origen: OrigenComision;
  montoBase: number;
  porcentajeAplicado: number;
  montoComision: number;
  estado: EstadoComision;
  createdAt: string;
}

export interface ReporteComisionesResponse {
  recepcionistaId: number;
  recepcionistaEmail: string;
  totalComisiones: number;
  detalle: ComisionResponse[];
}
