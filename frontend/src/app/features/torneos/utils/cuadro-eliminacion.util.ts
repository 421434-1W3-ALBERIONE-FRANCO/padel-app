import { PartidoTorneoResponse } from '../../../shared/models/torneo.model';

export interface CruceCuadro {
  /** Clave estable para el trackBy. */
  clave: string;
  /** Índice de la ronda dentro del cuadro (0 = primera). */
  ronda: number;
  /** Posición dentro de la ronda, de arriba hacia abajo. */
  indice: number;
  /** null: la ronda todavía no se generó y el cruce está "a definir". */
  partido: PartidoTorneoResponse | null;
  /** Índices, en la ronda anterior, de los cruces cuyos ganadores juegan éste. */
  origenes: number[];
  /** Altura del cruce en unidades de casillero (0 = primer cruce de la primera ronda). */
  centro: number;
}

export interface RondaCuadro {
  /** numeroRonda del backend (1 = primera). */
  numero: number;
  nombre: string;
  generada: boolean;
  cruces: CruceCuadro[];
}

export interface CaminoPareja {
  /** Cruces que la pareja jugó o tiene por jugar. */
  jugados: Set<string>;
  /** Casilleros "a definir" a los que llega si sigue ganando. */
  posibles: Set<string>;
  /** No perdió ningún partido (sigue en el torneo o lo ganó). */
  enCarrera: boolean;
}

/** Mismo criterio que TorneoServiceImpl.nombreRondaEliminacion. */
export function nombreRondaEliminacion(cantidadParejas: number): string {
  if (cantidadParejas <= 2) return 'Final';
  if (cantidadParejas <= 4) return 'Semifinal';
  if (cantidadParejas <= 8) return 'Cuartos de Final';
  if (cantidadParejas <= 16) return 'Octavos de Final';
  return `Ronda de ${cantidadParejas}`;
}

/**
 * Arma el cuadro completo: las rondas generadas con sus partidos y, después, las que faltan como
 * casilleros "a definir" hasta llegar a la final. Sin partidos, `parejasPrevistas` (las parejas
 * inscriptas) permite mostrar cómo va a quedar el cuadro antes del sorteo.
 */
export function construirCuadro(partidos: PartidoTorneoResponse[], parejasPrevistas = 0): RondaCuadro[] {
  const porRonda = new Map<number, PartidoTorneoResponse[]>();
  for (const partido of partidos) {
    porRonda.set(partido.numeroRonda, [...(porRonda.get(partido.numeroRonda) ?? []), partido]);
  }
  const numeros = [...porRonda.keys()].sort((a, b) => a - b);
  const generadas = numeros.map(n => [...porRonda.get(n)!].sort((a, b) => a.id - b.id));

  // El backend empareja a los ganadores de una ronda sin un orden fijo, así que el orden visual sale
  // de los datos: de la última ronda hacia la primera, cada cruce se ubica junto al de su rival.
  for (let r = generadas.length - 2; r >= 0; r--) {
    const sinUbicar = [...generadas[r]];
    const ordenada: PartidoTorneoResponse[] = [];
    const tomarGanador = (inscripcionId: number | null) => {
      const i = sinUbicar.findIndex(p => inscripcionId != null && p.ganadorInscripcionId === inscripcionId);
      if (i >= 0) ordenada.push(...sinUbicar.splice(i, 1));
    };
    for (const siguiente of generadas[r + 1]) {
      tomarGanador(siguiente.inscripcion1Id);
      tomarGanador(siguiente.inscripcion2Id);
    }
    generadas[r] = [...ordenada, ...sinUbicar];
  }

  const rondas: RondaCuadro[] = [];
  generadas.forEach((lista, r) => {
    const anterior = rondas[r - 1];
    rondas.push({
      numero: numeros[r],
      nombre: lista[0].ronda,
      generada: true,
      cruces: lista.map((partido, indice) => ({
        clave: `p${partido.id}`,
        ronda: r,
        indice,
        partido,
        origenes: anterior ? origenesPorGanador(anterior, partido) : [],
        centro: 0
      }))
    });
  });

  if (rondas.length === 0) {
    if (parejasPrevistas < 2) return [];
    rondas.push(rondaADefinir(null, nombreRondaEliminacion(parejasPrevistas), Math.ceil(parejasPrevistas / 2)));
  }

  // Rondas sin generar: cada cruce espera a los ganadores de dos cruces vecinos (o pasa directo,
  // como hace el backend con la pareja que queda sin rival cuando avanza un número impar).
  while (rondas[rondas.length - 1].cruces.length > 1) {
    const anterior = rondas[rondas.length - 1];
    rondas.push(rondaADefinir(anterior, nombreRondaEliminacion(anterior.cruces.length), Math.ceil(anterior.cruces.length / 2)));
  }

  ubicarCruces(rondas);
  return rondas;
}

function origenesPorGanador(anterior: RondaCuadro, partido: PartidoTorneoResponse): number[] {
  return [partido.inscripcion1Id, partido.inscripcion2Id]
    .map(id => id == null ? -1 : anterior.cruces.findIndex(c => c.partido?.ganadorInscripcionId === id))
    .filter(i => i >= 0);
}

function rondaADefinir(anterior: RondaCuadro | null, nombre: string, cantidad: number): RondaCuadro {
  const numero = anterior ? anterior.numero + 1 : 1;
  const ronda = anterior ? anterior.cruces[0].ronda + 1 : 0;
  return {
    numero,
    nombre,
    generada: false,
    cruces: Array.from({ length: cantidad }, (_, indice) => ({
      clave: `r${numero}-${indice}`,
      ronda,
      indice,
      partido: null,
      origenes: anterior ? [2 * indice, 2 * indice + 1].filter(i => i < anterior.cruces.length) : [],
      centro: 0
    }))
  };
}

/** Cada cruce queda a la altura media de los que lo alimentan, sin pisar al de arriba. */
function ubicarCruces(rondas: RondaCuadro[]): void {
  rondas.forEach((ronda, r) => {
    ronda.cruces.forEach((cruce, i) => {
      const alturas = r > 0 ? cruce.origenes.map(o => rondas[r - 1].cruces[o].centro) : [];
      let centro = alturas.length ? alturas.reduce((a, b) => a + b, 0) / alturas.length : i;
      if (i > 0) centro = Math.max(centro, ronda.cruces[i - 1].centro + 1);
      cruce.centro = centro;
    });
  });
}

export function participa(partido: PartidoTorneoResponse | null, inscripcionId: number | null): boolean {
  return !!partido && inscripcionId != null &&
    (partido.inscripcion1Id === inscripcionId || partido.inscripcion2Id === inscripcionId);
}

/** La final, si ya se jugó: su ganador es el campeón. */
export function finalJugada(rondas: RondaCuadro[]): PartidoTorneoResponse | null {
  const ultima = rondas[rondas.length - 1];
  const final = ultima?.cruces.length === 1 ? ultima.cruces[0].partido : null;
  return final?.estado === 'FINALIZADO' && final.ganadorInscripcionId != null ? final : null;
}

/** El recorrido de una pareja: lo que jugó y, si sigue en carrera, los casilleros hasta la final. */
export function caminoDePareja(rondas: RondaCuadro[], inscripcionId: number | null): CaminoPareja {
  const camino: CaminoPareja = { jugados: new Set(), posibles: new Set(), enCarrera: false };
  let ultimo: CruceCuadro | null = null;
  for (const ronda of rondas) {
    for (const cruce of ronda.cruces) {
      if (participa(cruce.partido, inscripcionId)) {
        camino.jugados.add(cruce.clave);
        ultimo = cruce;
      }
    }
  }
  if (!ultimo?.partido) return camino;

  camino.enCarrera = ultimo.partido.estado !== 'FINALIZADO' || ultimo.partido.ganadorInscripcionId === inscripcionId;
  let actual: CruceCuadro = ultimo;
  for (let r = actual.ronda + 1; camino.enCarrera && r < rondas.length; r++) {
    const siguiente = rondas[r].cruces.find(c => c.origenes.includes(actual.indice));
    if (!siguiente || siguiente.partido) break;
    camino.posibles.add(siguiente.clave);
    actual = siguiente;
  }
  return camino;
}
