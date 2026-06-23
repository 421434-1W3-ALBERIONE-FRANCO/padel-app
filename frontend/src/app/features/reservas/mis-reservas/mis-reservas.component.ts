import { Component, OnInit, signal, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ReservaService } from '../../../core/services/reserva.service';
import { AuthService } from '../../../core/services/auth.service';
import { ReservaResponse } from '../../../shared/models/reserva.model';

@Component({
  selector: 'app-mis-reservas',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './mis-reservas.component.html',
  styleUrls: ['./mis-reservas.component.css']
})
export class MisReservasComponent implements OnInit {
  private reservaService = inject(ReservaService);
  private authService = inject(AuthService);

  reservas = signal<ReservaResponse[]>([]);
  cargando = signal<boolean>(false);
  mensajeError = signal<string | null>(null);

  // Filtro / Vista para admin/recepcionista
  verTodas = signal<boolean>(false);
  esPersonal = computed(() => {
    const role = this.authService.userRole();
    return role === 'ADMIN' || role === 'RECEPCIONISTA';
  });

  // Modal de cancelación
  reservaParaCancelar = signal<ReservaResponse | null>(null);
  motivoCancelacion = signal<string>('');
  cargandoCancelacion = signal<boolean>(false);
  errorCancelacion = signal<string | null>(null);

  ngOnInit() {
    this.cargarReservas();
  }

  cargarReservas() {
    this.cargando.set(true);
    this.mensajeError.set(null);

    const request$ = (this.esPersonal() && this.verTodas())
      ? this.reservaService.getAllReservas()
      : this.reservaService.getMisReservas();

    request$.subscribe({
      next: (data) => {
        this.reservas.set(data);
        this.cargando.set(false);
      },
      error: (err) => {
        this.mensajeError.set(err.error?.message || 'Error al cargar las reservas.');
        this.cargando.set(false);
      }
    });
  }

  toggleVerTodas(valor: boolean) {
    this.verTodas.set(valor);
    this.cargarReservas();
  }

  abrirModalCancelacion(reserva: ReservaResponse) {
    this.reservaParaCancelar.set(reserva);
    this.motivoCancelacion.set('');
    this.errorCancelacion.set(null);
  }

  cerrarModalCancelacion() {
    this.reservaParaCancelar.set(null);
    this.errorCancelacion.set(null);
  }

  confirmarCancelacion() {
    const r = this.reservaParaCancelar();
    if (!r) return;

    this.cargandoCancelacion.set(true);
    this.errorCancelacion.set(null);

    this.reservaService.cancelarReserva(r.id, this.motivoCancelacion()).subscribe({
      next: () => {
        this.cargandoCancelacion.set(false);
        this.cerrarModalCancelacion();
        this.cargarReservas();
      },
      error: (err) => {
        this.cargandoCancelacion.set(false);
        this.errorCancelacion.set(err.error?.message || 'Error al cancelar la reserva.');
      }
    });
  }

  obtenerClaseEstado(estado: string): string {
    switch (estado) {
      case 'CONFIRMADA':
        return 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20';
      case 'PENDIENTE_PAGO':
        return 'bg-amber-500/10 text-amber-400 border border-amber-500/20';
      case 'CANCELADA':
        return 'bg-red-500/10 text-red-400 border border-red-500/20';
      case 'COMPLETADA':
        return 'bg-blue-500/10 text-blue-400 border border-blue-500/20';
      default:
        return 'bg-slate-800 text-slate-400 border border-slate-700';
    }
  }
}
