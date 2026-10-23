import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  BLOQUES_HORARIOS,
  BloqueDisponible,
  BloqueHorario,
  DIAS_SEMANA
} from '../../../shared/models/match.model';
import { DiaSemana } from '../../../shared/models/control-horario.model';

/**
 * Grilla semanal (7 días x mañana/tarde/noche). En modo editable cada celda alterna su
 * disponibilidad; en modo lectura resalta además los bloques que coinciden con los del usuario.
 */
@Component({
  selector: 'app-disponibilidad-grid',
  imports: [CommonModule],
  template: `
    <div class="gap-1" [ngClass]="compacto() ? 'inline-grid' : 'grid w-full max-w-md'" [style.grid-template-columns]="columnas()" role="grid" [attr.aria-label]="editable() ? 'Elegí tus horarios disponibles' : 'Disponibilidad semanal'">
      <span></span>
      @for (d of dias; track d) {
        <span class="text-center font-bold text-slate-500" [ngClass]="tamanoTexto()">{{ d.corto }}</span>
      }
    
      @for (b of bloques; track b) {
        <span class="flex items-center pr-1 font-semibold text-slate-500 whitespace-nowrap" [ngClass]="tamanoTexto()">
          {{ compacto() ? b.etiqueta.charAt(0) : b.etiqueta }}
        </span>
        @for (d of dias; track d) {
          <button
            type="button"
            role="gridcell"
            [disabled]="!editable()"
            (click)="alternar(d.valor, b.valor)"
            [attr.aria-pressed]="editable() ? estaSeleccionado(d.valor, b.valor) : null"
            [attr.aria-label]="d.largo + ' ' + b.etiqueta + (estaSeleccionado(d.valor, b.valor) ? ', disponible' : ', no disponible')"
            [ngClass]="claseCelda(d.valor, b.valor)"
            class="rounded-md border transition duration-150 disabled:cursor-default"
            [class.h-4]="compacto()" [class.w-5]="compacto()"
            [class.h-9]="!compacto()" [class.w-full]="!compacto()">
          </button>
        }
      }
    </div>
  `
})
export class DisponibilidadGridComponent {
  readonly seleccion = input<BloqueDisponible[]>([]);
  readonly resaltar = input<BloqueDisponible[]>([]);
  readonly editable = input(false);
  readonly compacto = input(false);
  readonly cambio = output<BloqueDisponible[]>();

  readonly dias = DIAS_SEMANA;
  readonly bloques = BLOQUES_HORARIOS;

  // Clases con valores arbitrarios de Tailwind (text-[10px]) van por ngClass: los bindings [class.x] no admiten corchetes.
  readonly tamanoTexto = computed(() => this.compacto() ? 'text-[10px]' : 'text-xs');
  // En modo editable las columnas se reparten el ancho disponible (en mobile no entran 7 celdas de 36px).
  readonly columnas = computed(() => this.compacto() ? 'auto repeat(7, 20px)' : 'auto repeat(7, minmax(0, 1fr))');

  private readonly claves = computed(() => new Set(this.seleccion().map(b => this.clave(b.diaSemana, b.bloque))));
  private readonly clavesResaltadas = computed(() => new Set(this.resaltar().map(b => this.clave(b.diaSemana, b.bloque))));

  estaSeleccionado(dia: DiaSemana, bloque: BloqueHorario): boolean {
    return this.claves().has(this.clave(dia, bloque));
  }

  claseCelda(dia: DiaSemana, bloque: BloqueHorario): string {
    const clave = this.clave(dia, bloque);
    if (this.clavesResaltadas().has(clave)) {
      return 'bg-primary-400 border-primary-300';
    }
    if (this.claves().has(clave)) {
      return this.editable()
        ? 'bg-primary-500/80 border-primary-400 hover:bg-primary-400 cursor-pointer'
        : 'bg-accent-500/40 border-accent-400/40';
    }
    return this.editable()
      ? 'bg-slate-950 border-slate-800 hover:border-primary-500/60 cursor-pointer'
      : 'bg-slate-950/60 border-slate-800/80';
  }

  alternar(dia: DiaSemana, bloque: BloqueHorario): void {
    if (!this.editable()) return;
    const actual = this.seleccion();
    const nueva = this.estaSeleccionado(dia, bloque)
      ? actual.filter(b => !(b.diaSemana === dia && b.bloque === bloque))
      : [...actual, { diaSemana: dia, bloque }];
    this.cambio.emit(nueva);
  }

  private clave(dia: DiaSemana, bloque: BloqueHorario): string {
    return `${dia}-${bloque}`;
  }
}
