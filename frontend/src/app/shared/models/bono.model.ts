import { MetodoMostrador } from './pago.model';

export type EstadoBono = 'ACTIVO' | 'VENCIDO' | 'AGOTADO';

export interface CrearBonoRequest {
  usuarioEmail: string;
  tipo: string;
  horasTotales: number;
  precioPagado: number;
  fechaVencimiento: string;
  /** Cómo se cobró la venta; obligatorio salvo que el bono sea sin cargo. */
  metodo?: MetodoMostrador | null;
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
