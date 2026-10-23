import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { ControlHorarioService } from '../../../core/services/control-horario.service';
import { ToastService } from '../../../core/services/toast.service';
import { FichajeResponse } from '../../models/control-horario.model';

/**
 * Fichaje del staff en el encabezado. La jornada ya no se abre sola con el login: se ficha entrada y salida a
 * mano (y, si el club lo configuró, solo desde su red).
 */
@Component({
  selector: 'app-fichaje',
  template: `
    @if (esStaff()) {
      @if (fichaje(); as f) {
        <div class="flex items-center gap-2 text-xs">
          @if (f.abierto) {
            <span class="hidden md:inline text-emerald-300 font-semibold" role="status">
              <span class="inline-block w-1.5 h-1.5 rounded-full bg-emerald-400 mr-1 align-middle"></span>En jornada desde {{ horaInicio(f) }}
            </span>
            <button type="button" (click)="ficharSalida()" [disabled]="procesando()"
              class="py-1.5 px-3 rounded-xl border border-emerald-500/30 text-emerald-300 font-bold hover:bg-emerald-500/10 transition disabled:opacity-50">
              Fichar salida
            </button>
          } @else {
            <button type="button" (click)="ficharEntrada()" [disabled]="procesando()"
              class="py-1.5 px-3 rounded-xl border border-primary-500/40 text-primary-300 font-bold hover:bg-primary-500/10 transition disabled:opacity-50">
              Fichar entrada
            </button>
          }
        </div>
      }
    }
  `
})
export class FichajeComponent implements OnInit {
  private authService = inject(AuthService);
  private controlHorarioService = inject(ControlHorarioService);
  private toast = inject(ToastService);

  esStaff = computed(() => {
    const rol = this.authService.userRole();
    return rol === 'ADMIN' || rol === 'RECEPCIONISTA';
  });
  fichaje = signal<FichajeResponse | null>(null);
  procesando = signal<boolean>(false);

  ngOnInit(): void {
    if (!this.esStaff()) return;
    // Si no se puede consultar, el widget no se muestra: no bloquea el resto de la pantalla.
    this.controlHorarioService.fichajeActual().subscribe({
      next: (f) => this.fichaje.set(f),
      error: () => this.fichaje.set(null)
    });
  }

  ficharEntrada(): void {
    this.procesando.set(true);
    this.controlHorarioService.ficharEntrada().subscribe({
      next: (f) => {
        this.procesando.set(false);
        this.fichaje.set(f);
        this.toast.success(`Entrada fichada a las ${this.horaInicio(f)}.`);
      },
      error: (err: HttpErrorResponse) => this.fallo(err, 'No se pudo fichar la entrada.')
    });
  }

  ficharSalida(): void {
    if (!confirm('¿Fichar la salida? Se cierra tu jornada de hoy.')) return;
    this.procesando.set(true);
    this.controlHorarioService.ficharSalida().subscribe({
      next: (f) => {
        this.procesando.set(false);
        this.fichaje.set(f);
        const minutos = f.minutosTrabajados ?? 0;
        this.toast.success(`Salida fichada: ${Math.floor(minutos / 60)} h ${minutos % 60} min trabajados.`);
      },
      error: (err: HttpErrorResponse) => this.fallo(err, 'No se pudo fichar la salida.')
    });
  }

  horaInicio(f: FichajeResponse): string {
    return f.inicio?.substring(11, 16) ?? '';
  }

  private fallo(err: HttpErrorResponse, porDefecto: string): void {
    this.procesando.set(false);
    // 403: fuera de la red del club; 409: ya había una jornada abierta (o no había ninguna).
    this.toast.error(err.error?.message || porDefecto);
    this.controlHorarioService.fichajeActual().subscribe({
      next: (f) => this.fichaje.set(f),
      error: () => {}
    });
  }
}
