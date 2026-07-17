export type CategoriaJugador = 'PRIMERA' | 'SEGUNDA' | 'TERCERA' | 'CUARTA' | 'QUINTA' | 'SEXTA' | 'SEPTIMA' | 'OCTAVA';

export type TipoTorneo = 'LIGA' | 'TORNEO' | 'TORNEO_EXPRESS';

export type FormatoTorneo = 'LIGA_TODOS_CONTRA_TODOS' | 'ELIMINACION_DIRECTA';

export type EstadoTorneo = 'INSCRIPCION_ABIERTA' | 'EN_CURSO' | 'FINALIZADO' | 'CANCELADO';

export type EstadoInscripcion = 'CONFIRMADA' | 'CANCELADA';

export type EstadoPartido = 'PENDIENTE' | 'FINALIZADO';

export interface TorneoRequest {
  nombre: string;
  tipo: TipoTorneo;
  formato: FormatoTorneo;
  categoria: CategoriaJugador;
  fechaInicio: string;
  fechaFin: string;
  maxParejas: number;
  precioInscripcion: number;
  descripcion: string | null;
}

export interface TorneoResponse {
  id: number;
  nombre: string;
  tipo: TipoTorneo;
  formato: FormatoTorneo;
  categoria: CategoriaJugador;
  fechaInicio: string;
  fechaFin: string;
  maxParejas: number;
  parejasInscriptas: number;
  precioInscripcion: number;
  descripcion: string | null;
  estado: EstadoTorneo;
}

export interface InscripcionTorneoRequest {
  companeroEmail: string;
}

export interface InscripcionTorneoResponse {
  id: number;
  torneoId: number;
  jugador1Id: number;
  jugador1Nombre: string;
  jugador1Email: string;
  jugador2Id: number;
  jugador2Nombre: string;
  jugador2Email: string;
  estado: EstadoInscripcion;
}

export interface ResultadoPartidoRequest {
  setsPareja1: number;
  setsPareja2: number;
}

export interface PartidoTorneoResponse {
  id: number;
  torneoId: number;
  ronda: string;
  numeroRonda: number;
  inscripcion1Id: number | null;
  inscripcion1Nombres: string | null;
  inscripcion2Id: number | null;
  inscripcion2Nombres: string | null;
  fechaHora: string | null;
  setsPareja1: number | null;
  setsPareja2: number | null;
  ganadorInscripcionId: number | null;
  estado: EstadoPartido;
}
