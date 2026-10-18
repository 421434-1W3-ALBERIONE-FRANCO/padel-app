import { Component, HostListener, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MetodoMostrador } from '../../models/pago.model';
import { formatearPesos } from '../../utils/moneda.util';

interface OpcionMetodo {
  valor: MetodoMostrador;
  etiqueta: string;
  detalle: string;
  icono: string;
}

/**
 * Modal para cobrar un turno en el mostrador: total, efectivo o tarjeta y, si corresponde, confirmar sin
 * cobrar ("paga al llegar"). Solo emite la elección: la operación y el cierre los maneja quien lo abre.
 */
@Component({
  selector: 'app-cobro-mostrador',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="fixed inset-0 z-[90] flex items-end sm:items-center justify-center bg-black/70 backdrop-blur-sm p-4" (click)="cerrarSiSePuede()">
      <div role="dialog" aria-modal="true" aria-labelledby="titulo-cobro" (click)="$event.stopPropagation()"
           class="w-full max-w-md rounded-3xl border border-white/10 bg-dark-950 p-6 shadow-2xl">
        <p class="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-500">Cobro en el mostrador</p>
        <h3 id="titulo-cobro" class="font-display text-xl font-bold text-white">{{ titulo() }}</h3>
        <p *ngIf="cliente()" class="mt-1 text-sm text-slate-300 truncate">{{ cliente() }}</p>
        <p *ngIf="detalle()" class="text-xs text-slate-500">{{ detalle() }}</p>

        <div class="mt-5 flex items-baseline justify-between rounded-2xl border border-white/5 bg-surface-1/60 px-4 py-3">
          <span class="text-[10px] font-bold uppercase tracking-[0.14em] text-slate-500">Total</span>
          <span class="font-display text-3xl font-extrabold text-white tabular-nums">{{ formatearPesos(monto()) }}</span>
        </div>

        <div class="mt-4 grid grid-cols-2 gap-2" role="radiogroup" aria-label="Medio de pago">
          <button *ngFor="let m of metodos" type="button" role="radio" [attr.aria-checked]="metodo() === m.valor"
                  (click)="metodo.set(m.valor)" [disabled]="procesando()"
                  class="rounded-2xl border px-4 py-3 text-left transition disabled:opacity-60"
                  [ngClass]="metodo() === m.valor
                    ? 'border-primary-500/60 bg-primary-500/10'
                    : 'border-white/10 bg-surface-1/40 hover:border-white/20'">
            <svg class="w-5 h-5 mb-1.5" [ngClass]="metodo() === m.valor ? 'text-primary-300' : 'text-slate-400'"
                 fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" [attr.d]="m.icono" />
            </svg>
            <span class="block text-sm font-bold text-white">{{ m.etiqueta }}</span>
            <span class="block text-[11px] text-slate-500">{{ m.detalle }}</span>
          </button>
        </div>

        <div class="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button type="button" (click)="cerrarSiSePuede()" [disabled]="procesando()" class="btn-secondary py-2.5 px-5 text-sm disabled:opacity-50">Cancelar</button>
          <button *ngIf="permitirSinCobro()" type="button" (click)="sinCobro.emit()" [disabled]="procesando()"
                  class="py-2.5 px-4 rounded-xl border border-white/10 text-sm font-semibold text-slate-300 hover:bg-white/5 transition disabled:opacity-50">
            Paga al llegar
          </button>
          <button type="button" (click)="cobrar.emit(metodo())" [disabled]="procesando()" class="btn-primary py-2.5 px-5 text-sm disabled:opacity-50">
            {{ procesando() ? 'Registrando...' : 'Registrar cobro' }}
          </button>
        </div>
        <p *ngIf="permitirSinCobro()" class="mt-3 text-[11px] text-slate-500">
          "Paga al llegar" confirma el turno sin cobrarlo: queda pendiente en la caja del día hasta que se cobre.
        </p>
      </div>
    </div>
  `
})
export class CobroMostradorComponent {
  titulo = input('Cobrar turno');
  cliente = input('');
  detalle = input('');
  monto = input.required<number>();
  procesando = input(false);
  /** Ofrece confirmar sin cobrar: solo para turnos pendientes de pago. */
  permitirSinCobro = input(false);

  cobrar = output<MetodoMostrador>();
  sinCobro = output<void>();
  cerrar = output<void>();

  readonly formatearPesos = formatearPesos;
  readonly metodos: OpcionMetodo[] = [
    {
      valor: 'EFECTIVO', etiqueta: 'Efectivo', detalle: 'Entra al cajón',
      icono: 'M2.25 18.75a60.07 60.07 0 0 1 15.797 2.101c.727.198 1.453-.342 1.453-1.096V18.75M3.75 4.5v.75A.75.75 0 0 1 3 6h-.75m0 0v-.375c0-.621.504-1.125 1.125-1.125H20.25M2.25 6v9m18-10.5v.75c0 .414.336.75.75.75h.75m-1.5-1.5h.375c.621 0 1.125.504 1.125 1.125v9.75c0 .621-.504 1.125-1.125 1.125h-.375m1.5-1.5H21a.75.75 0 0 0-.75.75v.75m0 0H3.75m0 0h-.375a1.125 1.125 0 0 1-1.125-1.125V15m1.5 1.5v-.75A.75.75 0 0 0 3 15h-.75M15 10.5a3 3 0 1 1-6 0 3 3 0 0 1 6 0Zm3 0h.008v.008H18V10.5Zm-12 0h.008v.008H6V10.5Z'
    },
    {
      valor: 'TARJETA', etiqueta: 'Tarjeta', detalle: 'Débito o crédito',
      icono: 'M2.25 8.25h19.5M2.25 9h19.5m-16.5 5.25h6m-6 2.25h3m-3.75 3h15a2.25 2.25 0 0 0 2.25-2.25V6.75A2.25 2.25 0 0 0 19.5 4.5h-15a2.25 2.25 0 0 0-2.25 2.25v10.5A2.25 2.25 0 0 0 4.5 19.5Z'
    }
  ];

  metodo = signal<MetodoMostrador>('EFECTIVO');

  @HostListener('document:keydown.escape')
  cerrarSiSePuede(): void {
    if (!this.procesando()) this.cerrar.emit();
  }
}
