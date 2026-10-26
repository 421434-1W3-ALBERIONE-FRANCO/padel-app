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

/** Contacto de otro jugador: solo llega cuando ya hay acuerdo (postulación aceptada). */
export interface ContactoResponse {
  email: string;
  telefono: string | null;
}

export interface PostulacionSolicitudRequest {
  mensaje: string | null;
}

export interface PostulacionSolicitudResponse {
  id: number;
  solicitudId: number;
  jugadorId: number;
  jugadorNombre: string;
  /** Solo para el creador de la solicitud, cuando la postulación está aceptada. */
  contacto: ContactoResponse | null;
  mensaje: string | null;
  estado: EstadoPostulacion;
  createdAt: string;
}

export interface SolicitudPartidoResponse {
  id: number;
  creadorId: number;
  creadorNombre: string;
  /** Solo para los postulantes aceptados (y el propio creador). */
  creadorContacto: ContactoResponse | null;
  tipoSolicitud: TipoSolicitud;
  categoria: CategoriaJugador;
  cantidadJugadoresFaltantes: number;
  fechaHoraPropuesta: string;
  canchaId: number | null;
  canchaNombre: string | null;
  descripcion: string | null;
  estado: EstadoSolicitud;
  createdAt: string;
  /** El creador ve todas las postulaciones; cualquier otro jugador, solo la suya. */
  postulaciones: PostulacionSolicitudResponse[];
  cantidadPostulaciones: number;
}
