import { Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  CandidatoMatchResponse,
  describirBloques,
  etiquetaPosicion
} from '../../../shared/models/match.model';
import { DisponibilidadGridComponent } from '../disponibilidad-grid/disponibilidad-grid.component';

/** Contenido de una carta del mazo: identidad, afinidad, nivel y horarios en común. */
@Component({
  selector: 'app-tarjeta-jugador',
  standalone: true,
  imports: [CommonModule, DisponibilidadGridComponent],
  template: `
    <div class="flex items-start gap-4">
      <div class="shrink-0 w-16 h-16 rounded-full p-[2px] bg-gradient-to-br from-primary-400 to-accent-500">
        <div class="w-full h-full rounded-full bg-surface-1 flex items-center justify-center text-xl font-extrabold font-display text-slate-100">
          {{ candidato().iniciales }}
        </div>
      </div>
      <div class="min-w-0 flex-1">
        <h3 class="text-xl font-bold text-slate-100 font-display truncate">{{ candidato().nombre }}</h3>
        <p class="text-xs text-slate-400 mt-0.5">
          {{ candidato().puntosRanking > 0 ? candidato().puntosRanking + ' pts en el ranking' : 'Sin puntos de ranking todavía' }}
        </p>
        <div class="flex flex-wrap gap-1.5 mt-2">
          <span class="text-[11px] font-bold px-2.5 py-1 rounded-lg bg-accent-500/15 text-accent-300 border border-accent-500/20">{{ candidato().categoria | titlecase }}</span>
          <span class="text-[11px] font-bold px-2.5 py-1 rounded-lg bg-primary-500/15 text-primary-300 border border-primary-500/20">{{ posicion() }}</span>
        </div>
      </div>
      <!-- Afinidad: anillo de progreso -->
      <div class="shrink-0 relative w-16 h-16" [attr.aria-label]="'Afinidad ' + candidato().afinidad + ' por ciento'">
        <svg viewBox="0 0 36 36" class="w-16 h-16 -rotate-90">
          <circle cx="18" cy="18" r="15.5" fill="none" stroke-width="3" class="stroke-slate-800"></circle>
          <circle cx="18" cy="18" r="15.5" fill="none" stroke-width="3" stroke-linecap="round" class="stroke-primary-400"
                  [attr.stroke-dasharray]="anillo()"></circle>
        </svg>
        <div class="absolute inset-0 flex flex-col items-center justify-center">
          <span class="text-sm font-extrabold text-primary-300 font-display">{{ candidato().afinidad }}%</span>
          <span class="text-[9px] uppercase tracking-wider text-slate-500">afín</span>
        </div>
      </div>
    </div>

    <p class="text-sm text-slate-300 leading-relaxed mt-5 min-h-[3rem]">
      {{ candidato().bio || 'Sin presentación. ¡Animate a escribirle si hay match!' }}
    </p>

    <div class="mt-5">
      <span class="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-2">Disponibilidad semanal</span>
      <app-disponibilidad-grid [seleccion]="candidato().disponibilidad" [resaltar]="candidato().coincidencias" [compacto]="true"></app-disponibilidad-grid>
    </div>

    <div class="mt-4 flex items-start gap-2 text-xs text-slate-300 bg-primary-500/5 border border-primary-500/15 rounded-xl px-3 py-2.5">
      <svg class="w-4 h-4 shrink-0 text-primary-400 mt-px" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
      <span><strong class="text-primary-300">Coinciden:</strong> {{ coincidencias() }}</span>
    </div>
  `
})
export class TarjetaJugadorComponent {
  readonly candidato = input.required<CandidatoMatchResponse>();

  readonly posicion = computed(() => etiquetaPosicion(this.candidato().posicion));
  readonly coincidencias = computed(() => describirBloques(this.candidato().coincidencias));
  // Circunferencia de r=15.5 ≈ 97.4: el trazo visible es proporcional a la afinidad.
  readonly anillo = computed(() => `${(this.candidato().afinidad / 100) * 97.4} 97.4`);
}
