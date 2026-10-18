import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PartidoTorneoResponse } from '../../../shared/models/torneo.model';
import { CeldaMatriz, FilaTablaLiga, PUNTOS_DERROTA, PUNTOS_VICTORIA, calcularTablaLiga, matrizLiga } from '../utils/tabla-liga.util';
import { abreviarPareja } from '../utils/torneo-visual.util';

@Component({
  selector: 'app-tabla-liga',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './tabla-liga.component.html'
})
export class TablaLigaComponent {
  partidos = input.required<PartidoTorneoResponse[]>();
  miInscripcionId = input<number | null>(null);
  puedeCargarResultados = input(false);
  /** Con la liga terminada, el primero de la tabla es el campeón. */
  finalizado = input(false);
  cargarResultado = output<PartidoTorneoResponse>();

  readonly puntosVictoria = PUNTOS_VICTORIA;
  readonly puntosDerrota = PUNTOS_DERROTA;

  tabla = computed(() => calcularTablaLiga(this.partidos()));
  matriz = computed(() => matrizLiga(this.tabla(), this.partidos()));

  /** Primero los partidos de la pareja del usuario; después, en el orden del fixture. */
  pendientes = computed(() => this.partidos()
    .filter(p => p.estado === 'PENDIENTE' && p.inscripcion2Id != null)
    .sort((a, b) => Number(this.esMio(b)) - Number(this.esMio(a)) || a.id - b.id));
  jugados = computed(() => this.partidos()
    .filter(p => p.estado === 'FINALIZADO')
    .sort((a, b) => b.id - a.id));
  progreso = computed(() => {
    const total = this.partidos().length;
    return total === 0 ? 0 : Math.round(this.jugados().length / total * 100);
  });

  esMiFila(fila: FilaTablaLiga): boolean {
    return fila.inscripcionId === this.miInscripcionId();
  }

  esMio(partido: PartidoTorneoResponse): boolean {
    const mia = this.miInscripcionId();
    return mia != null && (partido.inscripcion1Id === mia || partido.inscripcion2Id === mia);
  }

  claseFila(fila: FilaTablaLiga): string {
    if (this.esMiFila(fila)) return 'bg-accent-500/[0.08]';
    if (fila.posicion === 1 && this.finalizado()) return 'bg-amber-400/[0.06]';
    return '';
  }

  clasePosicion(fila: FilaTablaLiga): string {
    if (fila.posicion === 1) return 'bg-amber-400/20 text-amber-300 border-amber-400/40';
    if (fila.posicion <= 3) return 'bg-white/10 text-slate-200 border-white/15';
    return 'bg-transparent text-slate-500 border-white/5';
  }

  claseCelda(celda: CeldaMatriz): string {
    switch (celda.tipo) {
      case 'propia': return 'bg-white/[0.04]';
      case 'ganado': return 'bg-primary-500/15 text-primary-300 font-bold';
      case 'perdido': return 'bg-red-500/10 text-red-300/80';
      case 'pendiente': return 'text-slate-600';
      default: return 'text-slate-700';
    }
  }

  tituloCelda(fila: FilaTablaLiga, columna: FilaTablaLiga, celda: CeldaMatriz): string {
    const rival = abreviarPareja(columna.nombres);
    switch (celda.tipo) {
      case 'ganado': return `${abreviarPareja(fila.nombres)} le ganó ${celda.texto} a ${rival}`;
      case 'perdido': return `${abreviarPareja(fila.nombres)} perdió ${celda.texto} con ${rival}`;
      case 'pendiente': return `${abreviarPareja(fila.nombres)} contra ${rival}: por jugar`;
      default: return '';
    }
  }

  ganador(partido: PartidoTorneoResponse, inscripcionId: number | null): boolean {
    return partido.estado === 'FINALIZADO' && inscripcionId != null && partido.ganadorInscripcionId === inscripcionId;
  }

  abreviar(nombres: string | null): string {
    return abreviarPareja(nombres);
  }

  porInscripcion(_: number, fila: FilaTablaLiga): number {
    return fila.inscripcionId;
  }

  porPartido(_: number, partido: PartidoTorneoResponse): number {
    return partido.id;
  }
}
