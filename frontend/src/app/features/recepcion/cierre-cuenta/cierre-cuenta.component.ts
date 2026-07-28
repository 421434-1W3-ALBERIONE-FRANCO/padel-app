import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ReservaService } from '../../../core/services/reserva.service';
import { ConsumoService } from '../../../core/services/consumo.service';
import { ReservaResponse } from '../../../shared/models/reserva.model';
import { ConsumoResponse } from '../../../shared/models/consumo.model';
import { MetodoPago } from '../../../shared/models/pago.model';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-cierre-cuenta',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './cierre-cuenta.component.html',
  styleUrls: ['./cierre-cuenta.component.css']
})
export class CierreCuentaComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private toast = inject(ToastService);
  private router = inject(Router);
  private reservaService = inject(ReservaService);
  private consumoService = inject(ConsumoService);
  reservaId = signal<number>(0);
  reserva = signal<ReservaResponse | null>(null);
  consumos = signal<ConsumoResponse[]>([]);
  // UI state
  cargando = signal<boolean>(false);
  cargandoOperacion = signal<boolean>(false);
  // Form selections
  metodoSeleccionado = signal<MetodoPago>('EFECTIVO');
  ngOnInit() {
    const idParam = this.route.snapshot.paramMap.get('reservaId');
    if (idParam) {
      this.reservaId.set(Number(idParam));
      this.cargarDatos();
    } else {
      this.toast.error('ID de reserva inválido.');
    }
  }
  cargarDatos() {
    const id = this.reservaId();
    this.cargando.set(true);
    this.reservaService.getReserva(id).subscribe({
      next: (res) => {
        this.reserva.set(res);
        this.cargarConsumos(id);
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Error al obtener detalles del turno.');
        this.cargando.set(false);
      }
    });
  }
  cargarConsumos(reservaId: number) {
    this.consumoService.obtenerConsumos(reservaId).subscribe({
      next: (data) => {
        this.consumos.set(data);
        this.cargando.set(false);
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Error al cargar consumos de la reserva.');
        this.cargando.set(false);
      }
    });
  }
  obtenerPendientes(): ConsumoResponse[] {
    return this.consumos().filter(c => c.estadoPago === 'PENDIENTE');
  }
  obtenerPagados(): ConsumoResponse[] {
    return this.consumos().filter(c => c.estadoPago === 'PAGADO');
  }
  calcularTotalPendiente(): number {
    return this.obtenerPendientes().reduce((sum, item) => sum + item.subtotal, 0);
  }
  cerrarCuenta() {
    const total = this.calcularTotalPendiente();
    if (total === 0) {
      this.toast.error('No hay consumos pendientes para cobrar.');
      return;
    }
    const metodo = this.metodoSeleccionado();
    this.cargandoOperacion.set(true);
    this.consumoService.cerrarCuenta(this.reservaId(), { metodo }).subscribe({
      next: () => {
        this.cargandoOperacion.set(false);
        if (metodo === 'EFECTIVO') {
          this.toast.success('¡Cuenta cobrada y cerrada exitosamente en efectivo!');
        } else if (metodo === 'TARJETA') {
          this.toast.success('¡Cuenta cobrada y cerrada exitosamente con tarjeta!');
        } else if (metodo === 'MERCADOPAGO_POINT') {
          this.toast.success('¡Solicitud enviada a la terminal MercadoPago Point! La cuenta se marcará como pagada al confirmarse la transacción presencial.');
        }
        this.cargarConsumos(this.reservaId());
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Error al cerrar la cuenta.');
        this.cargandoOperacion.set(false);
      }
    });
  }
}
