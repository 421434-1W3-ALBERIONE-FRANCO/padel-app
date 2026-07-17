import { CategoriaJugador } from './torneo.model';

export type TipoSolicitud = 'BUSCA_PAREJA' | 'BUSCA_JUGADORES';

export type EstadoSolicitud = 'ABIERTA' | 'COMPLETA' | 'CANCELADA';

export type EstadoPostulacion = 'PENDIENTE' | 'ACEPTADA' | 'RECHAZADA';

export interface SolicitudPartidoRequest {
  tipoSolicitud: TipoSolicitud;
  categoria: CategoriaJugador;
  cantidadJugadoresFaltantes: number;
  fechaHoraPropuesta: string;
  canchaId: number | null;
  descripcion: string | null;
}

export interface PostulacionSolicitudRequest {
  mensaje: string | null;
}

export interface PostulacionSolicitudResponse {
  id: number;
  solicitudId: number;
  jugadorId: number;
  jugadorNombre: string;
  jugadorEmail: string;
  mensaje: string | null;
  estado: EstadoPostulacion;
  createdAt: string;
}

export interface SolicitudPartidoResponse {
  id: number;
  creadorId: number;
  creadorNombre: string;
  creadorEmail: string;
  tipoSolicitud: TipoSolicitud;
  categoria: CategoriaJugador;
  cantidadJugadoresFaltantes: number;
  fechaHoraPropuesta: string;
  canchaId: number | null;
  canchaNombre: string | null;
  descripcion: string | null;
  estado: EstadoSolicitud;
  createdAt: string;
  postulaciones: PostulacionSolicitudResponse[];
}
