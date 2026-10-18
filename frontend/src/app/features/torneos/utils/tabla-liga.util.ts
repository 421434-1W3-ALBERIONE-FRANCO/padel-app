import { PartidoTorneoResponse } from '../../../shared/models/torneo.model';

/** Los mismos puntos que suma el ranking del club (TorneoServiceImpl.PUNTOS_VICTORIA / PUNTOS_DERROTA). */
export const PUNTOS_VICTORIA = 3;
export const PUNTOS_DERROTA = 1;

/** Cantidad de resultados que muestra la racha de cada pareja. */
const LARGO_RACHA = 5;

export interface FilaTablaLiga {
  posicion: number;
  inscripcionId: number;
  nombres: string;
  jugados: number;
  ganados: number;
  perdidos: number;
  setsFavor: number;
  setsContra: number;
  diferenciaSets: number;
  puntos: number;
  /** Partidos que todavía le faltan jugar. */
  pendientes: number;
  /** Últimos resultados, del más viejo al más nuevo: G (ganó) o P (perdió). */
  racha: ('G' | 'P')[];
}

export type TipoCelda = 'propia' | 'sin-partido' | 'pendiente' | 'ganado' | 'perdido';

export interface CeldaMatriz {
  tipo: TipoCelda;
  /** Sets desde el punto de vista de la fila ("2-1"); vacío si no se jugó. */
  texto: string;
  partido: PartidoTorneoResponse | null;
}

/**
 * Tabla de posiciones de una liga a partir de sus partidos. Criterio: puntos (victoria 3, derrota 1);
 * a igualdad, diferencia de sets y después sets a favor; si siguen empatadas exactamente dos parejas,
 * va primero la que ganó el partido entre ellas.
 */
export function calcularTablaLiga(partidos: PartidoTorneoResponse[]): FilaTablaLiga[] {
  const filas = new Map<number, FilaTablaLiga>();
  const filaDe = (inscripcionId: number, nombres: string | null): FilaTablaLiga => {
    let fila = filas.get(inscripcionId);
    if (!fila) {
      fila = {
        posicion: 0, inscripcionId, nombres: nombres ?? '', jugados: 0, ganados: 0, perdidos: 0,
        setsFavor: 0, setsContra: 0, diferenciaSets: 0, puntos: 0, pendientes: 0, racha: []
      };
      filas.set(inscripcionId, fila);
    }
    return fila;
  };

  // En orden de id, que es el orden en que el backend crea el fixture: la racha queda cronológica.
  for (const partido of [...partidos].sort((a, b) => a.id - b.id)) {
    if (partido.inscripcion1Id == null || partido.inscripcion2Id == null) continue;
    const fila1 = filaDe(partido.inscripcion1Id, partido.inscripcion1Nombres);
    const fila2 = filaDe(partido.inscripcion2Id, partido.inscripcion2Nombres);
    if (!jugado(partido)) {
      fila1.pendientes++;
      fila2.pendientes++;
      continue;
    }
    const sets1 = partido.setsPareja1 ?? 0;
    const sets2 = partido.setsPareja2 ?? 0;
    sumarResultado(fila1, sets1, sets2, partido.ganadorInscripcionId === partido.inscripcion1Id);
    sumarResultado(fila2, sets2, sets1, partido.ganadorInscripcionId === partido.inscripcion2Id);
  }

  const tabla = [...filas.values()].sort((a, b) =>
    b.puntos - a.puntos
    || b.diferenciaSets - a.diferenciaSets
    || b.setsFavor - a.setsFavor
    || a.nombres.localeCompare(b.nombres));

  // Enfrentamiento directo: solo con un empate de exactamente dos parejas (con tres o más puede haber
  // un círculo de victorias y el criterio deja de ordenar).
  for (let i = 0; i < tabla.length - 1; i++) {
    const grupo = tabla.filter(f => empatadas(f, tabla[i]));
    if (grupo.length === 2 && empatadas(tabla[i], tabla[i + 1])) {
      const directo = partidoEntre(partidos, tabla[i].inscripcionId, tabla[i + 1].inscripcionId);
      if (directo && jugado(directo) && directo.ganadorInscripcionId === tabla[i + 1].inscripcionId) {
        [tabla[i], tabla[i + 1]] = [tabla[i + 1], tabla[i]];
      }
      i++;
    }
  }

  tabla.forEach((fila, i) => fila.posicion = i + 1);
  return tabla;
}

/** Matriz de todos contra todos con las parejas en el orden de la tabla. */
export function matrizLiga(tabla: FilaTablaLiga[], partidos: PartidoTorneoResponse[]): CeldaMatriz[][] {
  return tabla.map(fila => tabla.map(columna => {
    if (fila.inscripcionId === columna.inscripcionId) return { tipo: 'propia', texto: '', partido: null };
    const partido = partidoEntre(partidos, fila.inscripcionId, columna.inscripcionId);
    if (!partido) return { tipo: 'sin-partido', texto: '', partido: null };
    if (!jugado(partido)) return { tipo: 'pendiente', texto: '', partido };
    const esPareja1 = partido.inscripcion1Id === fila.inscripcionId;
    const favor = (esPareja1 ? partido.setsPareja1 : partido.setsPareja2) ?? 0;
    const contra = (esPareja1 ? partido.setsPareja2 : partido.setsPareja1) ?? 0;
    return {
      tipo: partido.ganadorInscripcionId === fila.inscripcionId ? 'ganado' : 'perdido',
      texto: `${favor}-${contra}`,
      partido
    };
  }));
}

export function partidoEntre(partidos: PartidoTorneoResponse[], a: number, b: number): PartidoTorneoResponse | null {
  return partidos.find(p =>
    (p.inscripcion1Id === a && p.inscripcion2Id === b) || (p.inscripcion1Id === b && p.inscripcion2Id === a)) ?? null;
}

function jugado(partido: PartidoTorneoResponse): boolean {
  return partido.estado === 'FINALIZADO' && partido.ganadorInscripcionId != null;
}

function sumarResultado(fila: FilaTablaLiga, favor: number, contra: number, gano: boolean): void {
  fila.jugados++;
  fila.setsFavor += favor;
  fila.setsContra += contra;
  fila.diferenciaSets = fila.setsFavor - fila.setsContra;
  if (gano) {
    fila.ganados++;
    fila.puntos += PUNTOS_VICTORIA;
  } else {
    fila.perdidos++;
    fila.puntos += PUNTOS_DERROTA;
  }
  fila.racha = [...fila.racha, gano ? 'G' as const : 'P' as const].slice(-LARGO_RACHA);
}

function empatadas(a: FilaTablaLiga, b: FilaTablaLiga): boolean {
  return a.puntos === b.puntos && a.diferenciaSets === b.diferenciaSets && a.setsFavor === b.setsFavor;
}
