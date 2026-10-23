import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReservaService } from '../../../core/services/reserva.service';
import { ToastService } from '../../../core/services/toast.service';
import { EstadoReserva, ReservaResponse } from '../../../shared/models/reserva.model';
import { PaginaResponse } from '../../../shared/models/pagina.model';
import { MetodoMostrador } from '../../../shared/models/pago.model';
import { CobroMostradorComponent } from '../../../shared/components/cobro-mostrador/cobro-mostrador.component';
import { formatearPesos } from '../../../shared/utils/moneda.util';

@Component({
  selector: 'app-reservas-admin',
  imports: [CommonModule, FormsModule, CobroMostradorComponent],
  templateUrl: './reservas-admin.component.html'
})
export class ReservasAdminComponent implements OnInit {
  private reservaService = inject(ReservaService);
  private toast = inject(ToastService);

  // La búsqueda y la paginación las hace el backend: antes se descargaba el historial completo para filtrar acá.
  pagina = signal<PaginaResponse<ReservaResponse> | null>(null);
  cargando = signal<boolean>(true);

  filtroEstado = signal<EstadoReserva | 'TODOS'>('TODOS');
  busqueda = signal<string>('');
  desde = signal<string>('');
  hasta = signal<string>('');

  // Modal de cancelación
  reservaParaCancelar = signal<ReservaResponse | null>(null);
  motivoCancelacion = '';
  cancelando = signal<boolean>(false);

  readonly estados: (EstadoReserva | 'TODOS')[] = ['TODOS', 'PENDIENTE_PAGO', 'CONFIRMADA', 'COMPLETADA', 'CANCELADA', 'NO_SHOW'];

  ngOnInit(): void {
    this.cargar();
  }

  /** Vuelve a la primera página con los filtros actuales. */
  buscar(): void {
    this.cargar(0);
  }

  cambiarEstado(estado: EstadoReserva | 'TODOS'): void {
    this.filtroEstado.set(estado);
    this.buscar();
  }

  irAPagina(numero: number): void {
    const actual = this.pagina();
    if (!actual || numero < 0 || numero >= actual.totalPages) return;
    this.cargar(numero);
  }

  cargar(numeroPagina = this.pagina()?.page ?? 0): void {
    this.cargando.set(true);
    const estado = this.filtroEstado();
    this.reservaService.buscarReservas({
      estado: estado === 'TODOS' ? undefined : estado,
      q: this.busqueda(),
      desde: this.desde() || undefined,
      hasta: this.hasta() || undefined,
      page: numeroPagina,
      size: 25
    }).subscribe({
      next: (data) => { this.pagina.set(data); this.cargando.set(false); },
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

  // Turno pendiente que se paga en el club: se cobra en el mostrador (queda en la caja del día) o se
  // confirma para que pague al llegar (queda como pendiente de cobro en la caja).
  reservaACobrar = signal<ReservaResponse | null>(null);
  cobrando = signal<boolean>(false);

  abrirCobro(r: ReservaResponse): void {
    this.reservaACobrar.set(r);
  }

  cerrarCobro(): void {
    this.reservaACobrar.set(null);
  }

  cobrar(metodo: MetodoMostrador): void {
    const r = this.reservaACobrar();
    if (!r) return;
    this.cobrando.set(true);
    this.reservaService.cobrarEnMostrador(r.id, metodo).subscribe({
      next: (pago) => {
        this.cobrando.set(false);
        this.reservaACobrar.set(null);
        this.toast.success(`Cobro registrado: ${formatearPesos(pago.monto)} ${metodo === 'EFECTIVO' ? 'en efectivo' : 'con tarjeta'}. Turno confirmado.`);
        this.cargar();
      },
      error: (err) => {
        this.cobrando.set(false);
        this.toast.error(err.error?.message || 'No se pudo registrar el cobro.');
      }
    });
  }

  confirmarSinCobro(): void {
    const r = this.reservaACobrar();
    if (!r) return;
    this.cobrando.set(true);
    this.reservaService.confirmarReserva(r.id).subscribe({
      next: () => {
        this.cobrando.set(false);
        this.reservaACobrar.set(null);
        this.toast.success('Turno confirmado para pagar al llegar. Queda pendiente de cobro en la caja del día.');
        this.cargar();
      },
      error: (err) => {
        this.cobrando.set(false);
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
