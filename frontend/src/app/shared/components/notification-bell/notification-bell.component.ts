import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { NotificacionService } from '../../../core/services/notificacion.service';
import { NotificacionResponse } from '../../models/notificacion.model';

@Component({
  selector: 'app-notification-bell',
  imports: [],
  template: `
    <div class="relative">
      <button
        (click)="toggleAbierto()"
        type="button"
        class="relative w-9 h-9 flex items-center justify-center rounded-xl bg-surface-1/40 border border-white/5 hover:bg-surface-1/60 transition cursor-pointer">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-5 h-5 text-slate-300">
          <path stroke-linecap="round" stroke-linejoin="round" d="M14.857 17.082a23.848 23.848 0 0 0 5.454-1.31A8.967 8.967 0 0 1 18 9.75V9A6 6 0 0 0 6 9v.75a8.967 8.967 0 0 1-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 0 1-5.714 0m5.714 0a3 3 0 1 1-5.714 0" />
        </svg>
        @if (contador() > 0) {
          <span
            class="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 rounded-full bg-accent-500 text-slate-950 text-[10px] font-bold flex items-center justify-center">
            {{ contador() > 9 ? '9+' : contador() }}
          </span>
        }
      </button>
    
      @if (abierto()) {
        <div
          class="absolute right-0 mt-2 w-80 max-h-96 overflow-y-auto bg-slate-950/95 border border-white/10 rounded-2xl shadow-2xl backdrop-blur-xl z-50">
          <div class="p-3 flex items-center justify-between border-b border-white/5 sticky top-0 bg-slate-950/95">
            <span class="text-xs font-bold text-slate-300 uppercase tracking-wider">Notificaciones</span>
            @if (contador() > 0) {
              <button (click)="marcarTodasLeidas()" class="text-[11px] text-primary-400 hover:underline cursor-pointer">
                Marcar todas leídas
              </button>
            }
          </div>
          @if (notificaciones().length === 0) {
            <div class="p-4 text-xs text-slate-400 text-center">
              No tenés notificaciones.
            </div>
          }
          @for (n of notificaciones(); track n) {
            <button
              (click)="marcarLeida(n)"
              type="button"
              class="w-full text-left p-3 border-b border-white/5 hover:bg-white/5 transition cursor-pointer"
              [class.opacity-50]="n.leida">
              <span class="block text-xs font-semibold text-white">{{ n.titulo }}</span>
              <span class="block text-[11px] text-slate-400 mt-0.5">{{ n.mensaje }}</span>
            </button>
          }
        </div>
      }
    </div>
  `
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  private notificacionService = inject(NotificacionService);
  private router = inject(Router);

  contador = this.notificacionService.contadorNoLeidas;
  notificaciones = this.notificacionService.notificaciones;
  abierto = signal<boolean>(false);

  ngOnInit(): void {
    this.notificacionService.iniciarPolling();
  }

  ngOnDestroy(): void {
    this.notificacionService.detenerPolling();
  }

  toggleAbierto(): void {
    this.abierto.set(!this.abierto());
    if (this.abierto()) {
      this.notificacionService.cargarMias().subscribe({ error: () => {} });
    }
  }

  marcarLeida(n: NotificacionResponse): void {
    if (!n.leida) {
      this.notificacionService.marcarLeida(n.id).subscribe({ error: () => {} });
    }
    // Los avisos del match llevan directo a "Mis matches" (contacto y turnos para jugar juntos).
    if (n.tipo === 'MATCH_NUEVO' || n.tipo === 'MATCH_TURNO_RESERVADO') {
      this.abierto.set(false);
      this.router.navigate(['/match'], { queryParams: { vista: 'matches' } });
    }
    // Las invitaciones a torneos se aceptan o rechazan desde el torneo.
    if ((n.tipo === 'INVITACION_TORNEO' || n.tipo === 'INVITACION_TORNEO_RESPONDIDA') && n.referenciaId != null) {
      this.abierto.set(false);
      this.router.navigate(['/torneos', n.referenciaId]);
    }
    // Al staff: devolución por entregar en el mostrador.
    if (n.tipo === 'REINTEGRO_PENDIENTE') {
      this.abierto.set(false);
      this.router.navigate(['/recepcion/caja']);
    }
  }

  marcarTodasLeidas(): void {
    this.notificacionService.marcarTodasLeidas().subscribe({ error: () => {} });
  }
}
