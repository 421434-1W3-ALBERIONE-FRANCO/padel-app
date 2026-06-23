export type EstadoBono = 'ACTIVO' | 'VENCIDO' | 'AGOTADO';

export interface CrearBonoRequest {
  usuarioEmail: string;
  tipo: string;
  horasTotales: number;
  precioPagado: number;
  fechaVencimiento: string;
}

export interface BonoResponse {
  id: number;
  usuarioId: number;
  usuarioEmail: string;
  tipo: string;
  horasTotales: number;
  horasUsadas: number;
  precioPagado: number;
  fechaVencimiento: string;
  estado: EstadoBono;
  createdAt: string;
  updatedAt: string | null;
}
