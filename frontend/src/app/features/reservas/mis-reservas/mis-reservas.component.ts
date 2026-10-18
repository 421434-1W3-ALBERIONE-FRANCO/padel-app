import { Component, OnInit, signal, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ReservaService } from '../../../core/services/reserva.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { ReservaResponse } from '../../../shared/models/reserva.model';
import { MetodoMostrador } from '../../../shared/models/pago.model';
import { CobroMostradorComponent } from '../../../shared/components/cobro-mostrador/cobro-mostrador.component';
import { formatearPesos } from '../../../shared/utils/moneda.util';

@Component({
  selector: 'app-mis-reservas',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, CobroMostradorComponent],
  templateUrl: './mis-reservas.component.html',
  styleUrls: ['./mis-reservas.component.css']
})
export class MisReservasComponent implements OnInit {
  private reservaService = inject(ReservaService);
  private authService = inject(AuthService);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);

  reservas = signal<ReservaResponse[]>([]);
  cargando = signal<boolean>(false);

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
    this.avisarResultadoDePago();
  }

  // MercadoPago vuelve a esta pantalla con ?status=success|pending (ver back_urls en PagoServiceImpl).
  // La confirmación llega por webhook unos segundos después, así que se avisa y se refresca la lista.
  private avisarResultadoDePago() {
    const status = this.route.snapshot.queryParamMap.get('status');
    if (status === 'success') {
      this.toast.success('¡Pago recibido! Tu turno se confirma en unos segundos.');
      setTimeout(() => this.cargarReservas(), 5000);
    } else if (status === 'pending') {
      this.toast.info('Tu pago quedó pendiente de acreditación. El turno se confirma cuando MercadoPago lo apruebe.');
    }
  }

  /** Un jugador no puede cancelar un turno que ya empezó (el backend también lo rechaza); el staff sí. */
  puedeCancelar(r: ReservaResponse): boolean {
    if (r.estadoReserva !== 'PENDIENTE_PAGO' && r.estadoReserva !== 'CONFIRMADA') return false;
    if (this.esPersonal()) return true;
    const inicio = new Date(`${r.fecha}T${r.horaInicio.substring(0, 5)}:00`);
    return inicio.getTime() > Date.now();
  }

  // Staff: turno pendiente que se paga en el club. Se cobra en el mostrador (queda en la caja del día) o se
  // confirma para que pague al llegar (queda pendiente de cobro en la caja).
  reservaACobrar = signal<ReservaResponse | null>(null);
  cobrando = signal<boolean>(false);

  cobrar(metodo: MetodoMostrador) {
    const r = this.reservaACobrar();
    if (!r) return;
    this.cobrando.set(true);
    this.reservaService.cobrarEnMostrador(r.id, metodo).subscribe({
      next: (pago) => {
        this.cobrando.set(false);
        this.reservaACobrar.set(null);
        this.toast.success(`Cobro registrado: ${formatearPesos(pago.monto)} ${metodo === 'EFECTIVO' ? 'en efectivo' : 'con tarjeta'}. Turno confirmado.`);
        this.cargarReservas();
      },
      error: (err) => {
        this.cobrando.set(false);
        this.toast.error(err.error?.message || 'No se pudo registrar el cobro.');
      }
    });
  }

  confirmarSinCobro() {
    const r = this.reservaACobrar();
    if (!r) return;
    this.cobrando.set(true);
    this.reservaService.confirmarReserva(r.id).subscribe({
      next: () => {
        this.cobrando.set(false);
        this.reservaACobrar.set(null);
        this.toast.success('Turno confirmado para pagar al llegar. Queda pendiente de cobro en la caja del día.');
        this.cargarReservas();
      },
      error: (err) => {
        this.cobrando.set(false);
        this.toast.error(err.error?.message || 'No se pudo confirmar la reserva.');
      }
    });
  }

  cargarReservas() {
    this.cargando.set(true);

    const request$ = (this.esPersonal() && this.verTodas())
      ? this.reservaService.getAllReservas()
      : this.reservaService.getMisReservas();

    request$.subscribe({
      next: (data) => {
        this.reservas.set(data);
        this.cargando.set(false);
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Error al cargar las reservas.');
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
        this.toast.success('Reserva cancelada. La franja quedó liberada.');
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
