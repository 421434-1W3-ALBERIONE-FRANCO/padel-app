import { CategoriaJugador } from './torneo.model';
import { DiaSemana } from './control-horario.model';

export type PosicionJuego = 'DRIVE' | 'REVES' | 'INDISTINTO';
export type BloqueHorario = 'MANANA' | 'TARDE' | 'NOCHE';
export type TipoSwipe = 'LIKE' | 'PASS';

export interface BloqueDisponible {
  diaSemana: DiaSemana;
  bloque: BloqueHorario;
}

export interface PerfilJugadorRequest {
  categoria: CategoriaJugador;
  posicion: PosicionJuego;
  bio: string | null;
  visibleEnMatch: boolean;
  disponibilidad: BloqueDisponible[];
}

// El backend omite los campos null (default-property-inclusion: non_null): por eso van opcionales.
export interface PerfilJugadorResponse {
  id: number;
  usuarioId: number;
  categoria: CategoriaJugador;
  posicion: PosicionJuego;
  bio?: string | null;
  visibleEnMatch: boolean;
  disponibilidad: BloqueDisponible[];
}

export interface CandidatoMatchResponse {
  usuarioId: number;
  nombre: string;
  iniciales: string;
  categoria: CategoriaJugador;
  posicion: PosicionJuego;
  bio?: string | null;
  puntosRanking: number;
  afinidad: number;
  disponibilidad: BloqueDisponible[];
  coincidencias: BloqueDisponible[];
}

export interface MatchParejaResponse {
  id: number;
  companeroId: number;
  nombre: string;
  iniciales: string;
  categoria?: CategoriaJugador | null;
  posicion?: PosicionJuego | null;
  email: string;
  telefono?: string | null;
  coincidencias: BloqueDisponible[];
  createdAt?: string;
}

export interface SwipeRequest {
  usuarioId: number;
  tipo: TipoSwipe;
}

export interface SwipeResponse {
  match: boolean;
  pareja?: MatchParejaResponse | null;
}

export interface TurnoSugeridoResponse {
  fecha: string;
  canchaId: number;
  canchaNombre: string;
  franjaId: number;
  horaInicio: string;
  horaFin: string;
  duracionMin: number;
  precio: number;
  bloque: BloqueHorario;
}

export interface ReservaMatchRequest {
  canchaId: number;
  franjaId: number;
  fecha: string;
  duracionMin?: number;
}

export const DIAS_SEMANA: { valor: DiaSemana; corto: string; largo: string }[] = [
  { valor: 'MONDAY', corto: 'L', largo: 'Lun' },
  { valor: 'TUESDAY', corto: 'M', largo: 'Mar' },
  { valor: 'WEDNESDAY', corto: 'X', largo: 'Mié' },
  { valor: 'THURSDAY', corto: 'J', largo: 'Jue' },
  { valor: 'FRIDAY', corto: 'V', largo: 'Vie' },
  { valor: 'SATURDAY', corto: 'S', largo: 'Sáb' },
  { valor: 'SUNDAY', corto: 'D', largo: 'Dom' }
];

export const BLOQUES_HORARIOS: { valor: BloqueHorario; etiqueta: string; rango: string }[] = [
  { valor: 'MANANA', etiqueta: 'Mañana', rango: '8 a 12 h' },
  { valor: 'TARDE', etiqueta: 'Tarde', rango: '12 a 18 h' },
  { valor: 'NOCHE', etiqueta: 'Noche', rango: '18 a 24 h' }
];

export const POSICIONES: { valor: PosicionJuego; etiqueta: string; detalle: string }[] = [
  { valor: 'DRIVE', etiqueta: 'Drive', detalle: 'Lado derecho' },
  { valor: 'REVES', etiqueta: 'Revés', detalle: 'Lado izquierdo' },
  { valor: 'INDISTINTO', etiqueta: 'Indistinto', detalle: 'Me adapto' }
];

/** "Mar noche, Jue noche" a partir de una lista de bloques. */
export function describirBloques(bloques: BloqueDisponible[]): string {
  return bloques
    .map(b => {
      const dia = DIAS_SEMANA.find(d => d.valor === b.diaSemana)?.largo ?? b.diaSemana;
      const bloque = BLOQUES_HORARIOS.find(x => x.valor === b.bloque)?.etiqueta.toLowerCase() ?? b.bloque;
      return `${dia} ${bloque}`;
    })
    .join(', ');
}

export function etiquetaPosicion(posicion: PosicionJuego | null | undefined): string {
  return POSICIONES.find(p => p.valor === posicion)?.etiqueta ?? 'Sin definir';
}
