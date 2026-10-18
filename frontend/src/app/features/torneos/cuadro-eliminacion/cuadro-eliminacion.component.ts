import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PartidoTorneoResponse } from '../../../shared/models/torneo.model';
import {
  CruceCuadro,
  RondaCuadro,
  caminoDePareja,
  construirCuadro,
  finalJugada,
  participa
} from '../utils/cuadro-eliminacion.util';
import { abreviarPareja, jugadoresDePareja } from '../utils/torneo-visual.util';

/** Medidas del cuadro en px: los casilleros se ubican en absoluto y las líneas se dibujan en un SVG. */
const ANCHO = 204;
const ALTO = 84;
const SEPARACION_X = 44;
const SEPARACION_Y = 18;
const ANCHO_CAMPEON = 176;
const RADIO_CODO = 8;

type TipoConector = 'mio' | 'posible' | 'avance' | 'normal';

interface CasilleroVista {
  cruce: CruceCuadro;
  x: number;
  y: number;
  mio: boolean;
  posible: boolean;
  esFinal: boolean;
}

interface ConectorVista {
  clave: string;
  d: string;
  tipo: TipoConector;
}

@Component({
  selector: 'app-cuadro-eliminacion',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './cuadro-eliminacion.component.html'
})
export class CuadroEliminacionComponent {
  partidos = input.required<PartidoTorneoResponse[]>();
  /** Parejas inscriptas: sin fixture, permite mostrar el cuadro vacío. */
  parejasPrevistas = input(0);
  miInscripcionId = input<number | null>(null);
  puedeCargarResultados = input(false);
  cargarResultado = output<PartidoTorneoResponse>();

  readonly ancho = ANCHO;
  readonly alto = ALTO;
  readonly anchoCampeon = ANCHO_CAMPEON;

  rondas = computed(() => construirCuadro(this.partidos(), this.parejasPrevistas()));
  camino = computed(() => caminoDePareja(this.rondas(), this.miInscripcionId()));
  final = computed(() => finalJugada(this.rondas()));
  campeonEsMio = computed(() => {
    const final = this.final();
    return !!final && final.ganadorInscripcionId === this.miInscripcionId();
  });
  /** La pareja del usuario está en el cuadro y todavía puede llegar a la copa. */
  campeonPosible = computed(() => !this.final() && this.camino().enCarrera);

  vista = computed(() => {
    const rondas = this.rondas();
    const camino = this.camino();
    const ultima = rondas.length - 1;

    const casilleros: CasilleroVista[] = rondas.flatMap((ronda, r) => ronda.cruces.map(cruce => ({
      cruce,
      x: this.xRonda(r),
      y: cruce.centro * (ALTO + SEPARACION_Y),
      mio: camino.jugados.has(cruce.clave),
      posible: camino.posibles.has(cruce.clave),
      esFinal: r === ultima
    })));
    const porClave = new Map(casilleros.map(c => [c.cruce.clave, c]));

    const conectores: ConectorVista[] = [];
    rondas.forEach((ronda, r) => {
      if (r === 0) return;
      for (const cruce of ronda.cruces) {
        const destino = porClave.get(cruce.clave)!;
        for (const o of cruce.origenes) {
          const origen = porClave.get(rondas[r - 1].cruces[o].clave)!;
          conectores.push({
            clave: `${origen.cruce.clave}>${cruce.clave}`,
            d: codo(origen.x + ANCHO, centroY(origen), destino.x, centroY(destino)),
            tipo: this.tipoConector(origen, destino)
          });
        }
      }
    });

    // La última ronda tiene un solo cruce: la final, que desemboca en el casillero del campeón.
    const casilleroFinal = casilleros[casilleros.length - 1];
    const xCampeon = this.xRonda(rondas.length);
    if (casilleroFinal) {
      conectores.push({
        clave: 'campeon',
        d: codo(casilleroFinal.x + ANCHO, centroY(casilleroFinal), xCampeon, centroY(casilleroFinal)),
        tipo: this.campeonEsMio() ? 'mio'
          : this.campeonPosible() && (casilleroFinal.mio || casilleroFinal.posible) ? 'posible'
          : this.final() ? 'avance' : 'normal'
      });
    }

    const alturaMaxima = Math.max(0, ...casilleros.map(c => c.y));
    return {
      casilleros,
      conectores,
      xCampeon,
      yCampeon: casilleroFinal?.y ?? 0,
      ancho: xCampeon + ANCHO_CAMPEON,
      alto: alturaMaxima + ALTO
    };
  });

  xRonda(r: number): number {
    return r * (ANCHO + SEPARACION_X);
  }

  private tipoConector(origen: CasilleroVista, destino: CasilleroVista): TipoConector {
    if (origen.mio && destino.mio) return 'mio';
    if ((origen.mio || origen.posible) && destino.posible) return 'posible';
    const ganador = origen.cruce.partido?.ganadorInscripcionId ?? null;
    return participa(destino.cruce.partido, ganador) ? 'avance' : 'normal';
  }

  estadoRonda(ronda: RondaCuadro): 'jugada' | 'en-juego' | 'a-definir' {
    if (!ronda.generada) return 'a-definir';
    return ronda.cruces.every(c => c.partido?.estado === 'FINALIZADO') ? 'jugada' : 'en-juego';
  }

  puedeCargar(casillero: CasilleroVista): boolean {
    const p = casillero.cruce.partido;
    return this.puedeCargarResultados() && !!p && p.estado === 'PENDIENTE' && p.inscripcion2Id != null;
  }

  claseCasillero(casillero: CasilleroVista): string {
    if (!casillero.cruce.partido) {
      return casillero.posible
        ? 'border-dashed border-accent-400/60 bg-accent-500/[0.06]'
        : 'border-dashed border-white/10 bg-white/[0.02]';
    }
    if (casillero.mio) return 'border-accent-400/70 bg-surface-2 shadow-[0_0_28px_rgba(168,85,247,0.28)]';
    if (casillero.esFinal) return 'border-amber-400/30 bg-surface-2';
    return 'border-white/10 bg-surface-2';
  }

  claseFila(partido: PartidoTorneoResponse, inscripcionId: number | null): string {
    if (partido.estado !== 'FINALIZADO') return 'text-slate-200';
    return partido.ganadorInscripcionId === inscripcionId
      ? 'bg-primary-500/[0.09] text-white font-semibold'
      : 'text-slate-500';
  }

  claseSets(partido: PartidoTorneoResponse, inscripcionId: number | null): string {
    return partido.ganadorInscripcionId === inscripcionId ? 'text-primary-300' : 'text-slate-500';
  }

  claseConector(tipo: TipoConector): string {
    switch (tipo) {
      case 'mio': return 'stroke-accent-400';
      case 'posible': return 'stroke-accent-400/60';
      case 'avance': return 'stroke-white/30';
      default: return 'stroke-white/10';
    }
  }

  esMiPareja(inscripcionId: number | null): boolean {
    return inscripcionId != null && inscripcionId === this.miInscripcionId();
  }

  abreviar(nombres: string | null): string {
    return abreviarPareja(nombres);
  }

  jugadores(nombres: string | null): string[] {
    return jugadoresDePareja(nombres);
  }

  porCasillero(_: number, casillero: CasilleroVista): string {
    return casillero.cruce.clave;
  }

  porConector(_: number, conector: ConectorVista): string {
    return conector.clave;
  }

  porNumero(_: number, ronda: RondaCuadro): number {
    return ronda.numero;
  }
}

function centroY(casillero: CasilleroVista): number {
  return casillero.y + ALTO / 2;
}

/** Línea de un cruce al siguiente: sale horizontal, baja (o sube) por el medio y entra horizontal. */
function codo(x1: number, y1: number, x2: number, y2: number): string {
  if (Math.abs(y2 - y1) < 1) return `M${x1} ${y1}H${x2}`;
  const xm = (x1 + x2) / 2;
  const s = y2 > y1 ? 1 : -1;
  const r = Math.min(RADIO_CODO, Math.abs(y2 - y1) / 2, xm - x1);
  return `M${x1} ${y1}H${xm - r}Q${xm} ${y1} ${xm} ${y1 + s * r}V${y2 - s * r}Q${xm} ${y2} ${xm + r} ${y2}H${x2}`;
}
