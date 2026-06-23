import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ReservaService } from '../../../core/services/reserva.service';
import { ProductoService } from '../../../core/services/producto.service';
import { ConsumoService } from '../../../core/services/consumo.service';
import { ReservaResponse } from '../../../shared/models/reserva.model';
import { ProductoResponse } from '../../../shared/models/producto.model';
import { ConsumoResponse } from '../../../shared/models/consumo.model';

@Component({
  selector: 'app-carga-consumos',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink],
  templateUrl: './carga-consumos.component.html',
  styleUrls: ['./carga-consumos.component.css']
})
export class CargaConsumosComponent implements OnInit {
  private reservaService = inject(ReservaService);
  private productoService = inject(ProductoService);
  private consumoService = inject(ConsumoService);
  private router = inject(Router);
  private fb = inject(FormBuilder);

  // Data signals
  reservasHoy = signal<ReservaResponse[]>([]);
  productos = signal<ProductoResponse[]>([]);
  consumosTab = signal<ConsumoResponse[]>([]);
  reservaSeleccionada = signal<ReservaResponse | null>(null);

  // UI state signals
  cargandoReservas = signal<boolean>(false);
  cargandoTab = signal<boolean>(false);
  mensajeError = signal<string | null>(null);
  mensajeExito = signal<string | null>(null);

  consumoForm!: FormGroup;

  ngOnInit() {
    this.initForm();
    this.cargarReservasHoy();
    this.cargarProductos();
  }

  initForm() {
    this.consumoForm = this.fb.group({
      productoId: [null, Validators.required],
      cantidad: [1, [Validators.required, Validators.min(1)]]
    });
  }

  cargarReservasHoy() {
    this.cargandoReservas.set(true);
    this.mensajeError.set(null);
    const hoyStr = new Date().toISOString().substring(0, 10); // YYYY-MM-DD

    this.reservaService.getAllReservas().subscribe({
      next: (data) => {
        // Filtramos reservas de hoy que no estén canceladas
        const filtradas = data.filter(r => r.fecha === hoyStr && r.estadoReserva !== 'CANCELADA');
        this.reservasHoy.set(filtradas);
        this.cargandoReservas.set(false);
      },
      error: (err) => {
        this.mensajeError.set(err.error?.message || 'Error al cargar las reservas del día.');
        this.cargandoReservas.set(false);
      }
    });
  }

  cargarProductos() {
    this.productoService.listarActivos().subscribe({
      next: (data) => {
        this.productos.set(data.filter(p => p.stock > 0)); // Solo mostramos los que tienen stock
      },
      error: (err) => {
        console.error('Error al cargar productos activos:', err);
      }
    });
  }

  seleccionarReserva(reserva: ReservaResponse) {
    this.reservaSeleccionada.set(reserva);
    this.mensajeError.set(null);
    this.mensajeExito.set(null);
    this.consumoForm.patchValue({ productoId: null, cantidad: 1 });
    this.cargarConsumosTab(reserva.id);
  }

  deseleccionarReserva() {
    this.reservaSeleccionada.set(null);
    this.consumosTab.set([]);
    this.mensajeError.set(null);
    this.mensajeExito.set(null);
  }

  cargarConsumosTab(reservaId: number) {
    this.cargandoTab.set(true);
    this.consumoService.obtenerConsumos(reservaId).subscribe({
      next: (data) => {
        this.consumosTab.set(data);
        this.cargandoTab.set(false);
      },
      error: (err) => {
        this.mensajeError.set(err.error?.message || 'Error al cargar los consumos de la reserva.');
        this.cargandoTab.set(false);
      }
    });
  }

  cargarConsumo() {
    const reserva = this.reservaSeleccionada();
    if (!reserva || this.consumoForm.invalid) {
      this.consumoForm.markAllAsTouched();
      return;
    }

    const payload = this.consumoForm.value;
    this.cargandoTab.set(true);
    this.mensajeError.set(null);
    this.mensajeExito.set(null);

    this.consumoService.cargarConsumo(reserva.id, payload).subscribe({
      next: () => {
        this.mensajeExito.set('Consumo cargado con éxito');
        this.consumoForm.patchValue({ productoId: null, cantidad: 1 });
        this.cargarConsumosTab(reserva.id);
        this.cargarProductos(); // actualiza stocks locales en el dropdown
      },
      error: (err) => {
        this.mensajeError.set(err.error?.message || 'Error al cargar el consumo. Verifique stock.');
        this.cargandoTab.set(false);
      }
    });
  }

  calcularTotalTab(): number {
    return this.consumosTab().reduce((sum, item) => sum + item.subtotal, 0);
  }

  irAFacturacion() {
    const reserva = this.reservaSeleccionada();
    if (reserva) {
      this.router.navigate(['/recepcion/cierre-cuenta', reserva.id]);
    }
  }
}
