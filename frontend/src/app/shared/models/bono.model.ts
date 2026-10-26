import { MetodoMostrador } from './pago.model';

export type EstadoBono = 'ACTIVO' | 'VENCIDO' | 'AGOTADO';

/** Venta de un bono del catálogo: horas, precio y vigencia los define el tipo, no quien vende. */
export interface CrearBonoRequest {
  usuarioEmail: string;
  tipoBonoId: number;
  /** Cómo se cobró la venta; obligatorio salvo que el bono sea sin cargo. */
  metodo?: MetodoMostrador | null;
  /** Solo un ADMIN puede regalar un bono, y tiene que dejar el motivo. */
  sinCargo: boolean;
  motivoSinCargo?: string | null;
}

export interface BonoResponse {
  id: number;
  usuarioId: number;
  usuarioEmail: string;
  /** Tipo del catálogo del que salió; null en los bonos vendidos antes del catálogo. */
  tipoBonoId: number | null;
  tipo: string;
  horasTotales: number;
  horasUsadas: number;
  precioPagado: number;
  fechaVencimiento: string;
  estado: EstadoBono;
  createdAt: string;
  updatedAt: string | null;
}

/** Tipo de bono del catálogo que administra el ADMIN. */
export interface TipoBonoRequest {
  nombre: string;
  descripcion: string | null;
  horas: number;
  precio: number;
  diasVigencia: number;
  activo?: boolean | null;
}

export interface TipoBonoResponse {
  id: number;
  nombre: string;
  descripcion: string | null;
  horas: number;
  precio: number;
  diasVigencia: number;
  activo: boolean;
}
