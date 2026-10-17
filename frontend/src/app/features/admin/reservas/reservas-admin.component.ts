import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReservaService } from '../../../core/services/reserva.service';
import { ToastService } from '../../../core/services/toast.service';
import { EstadoReserva, ReservaResponse } from '../../../shared/models/reserva.model';

@Component({
  selector: 'app-reservas-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reservas-admin.component.html'
})
export class ReservasAdminComponent implements OnInit {
  private reservaService = inject(ReservaService);
  private toast = inject(ToastService);

  reservas = signal<ReservaResponse[]>([]);
  cargando = signal<boolean>(true);

  filtroEstado = signal<EstadoReserva | 'TODOS'>('TODOS');
  busqueda = signal<string>('');

  // Modal de cancelación
  reservaParaCancelar = signal<ReservaResponse | null>(null);
  motivoCancelacion = '';
  cancelando = signal<boolean>(false);

  readonly estados: (EstadoReserva | 'TODOS')[] = ['TODOS', 'PENDIENTE_PAGO', 'CONFIRMADA', 'COMPLETADA', 'CANCELADA', 'NO_SHOW'];

  filtradas = computed(() => {
    const est = this.filtroEstado();
    const q = this.busqueda().trim().toLowerCase();
    return this.reservas()
      .filter(r => est === 'TODOS' || r.estadoReserva === est)
      .filter(r => !q ||
        r.usuarioEmail?.toLowerCase().includes(q) ||
        r.canchaNombre?.toLowerCase().includes(q) ||
        r.fecha?.includes(q))
      .sort((a, b) => (b.fecha + b.horaInicio).localeCompare(a.fecha + a.horaInicio));
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.reservaService.getAllReservas().subscribe({
      next: (data) => { this.reservas.set(data); this.cargando.set(false); },
      error: (err) => { this.toast.error(err.error?.message || 'Error al cargar las reservas.'); this.cargando.set(false); }
    });
  }

  puedeCancelar(r: ReservaResponse): boolean {
    return r.estadoReserva === 'PENDIENTE_PAGO' || r.estadoReserva === 'CONFIRMADA';
  }

  abrirCancelacion(r: ReservaResponse): void {
    this.reservaParaCancelar.set(r);
    this.motivoCancelacion = '';
  }

  cerrarCancelacion(): void {
    this.reservaParaCancelar.set(null);
  }

  confirmarCancelacion(): void {
    const r = this.reservaParaCancelar();
    if (!r) return;
    this.cancelando.set(true);
    this.reservaService.cancelarReserva(r.id, this.motivoCancelacion).subscribe({
      next: () => {
        this.cancelando.set(false);
        this.cerrarCancelacion();
        this.toast.success('Reserva cancelada. La franja quedó liberada.');
        this.cargar();
      },
      error: (err) => {
        this.cancelando.set(false);
        this.toast.error(err.error?.message || 'No se pudo cancelar la reserva.');
      }
    });
  }

  // Confirmación manual de una reserva pendiente que se cobra en el club (efectivo / mostrador).
  confirmandoId = signal<number | null>(null);

  confirmarPago(r: ReservaResponse): void {
    this.confirmandoId.set(r.id);
    this.reservaService.confirmarReserva(r.id).subscribe({
      next: () => {
        this.confirmandoId.set(null);
        this.toast.success('Reserva confirmada.');
        this.cargar();
      },
      error: (err) => {
        this.confirmandoId.set(null);
        this.toast.error(err.error?.message || 'No se pudo confirmar la reserva.');
      }
    });
  }

  badgeClase(estado: EstadoReserva): string {
    switch (estado) {
      case 'CONFIRMADA': return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20';
      case 'COMPLETADA': return 'bg-primary-500/10 text-primary-400 border-primary-500/20';
      case 'PENDIENTE_PAGO': return 'bg-amber-500/10 text-amber-400 border-amber-500/20';
      case 'CANCELADA': return 'bg-red-500/10 text-red-400 border-red-500/20';
      default: return 'bg-slate-500/10 text-slate-400 border-slate-500/20';
    }
  }
}
