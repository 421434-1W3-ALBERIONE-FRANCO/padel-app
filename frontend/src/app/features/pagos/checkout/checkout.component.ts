import { Component, OnInit, signal, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ReservaService } from '../../../core/services/reserva.service';
import { PagoService } from '../../../core/services/pago.service';
import { BonoService } from '../../../core/services/bono.service';
import { ReservaResponse } from '../../../shared/models/reserva.model';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.css']
})
export class CheckoutComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private reservaService = inject(ReservaService);
  private pagoService = inject(PagoService);
  private bonoService = inject(BonoService);

  reservaId: number | null = null;
  reserva = signal<ReservaResponse | null>(null);
  cargando = signal<boolean>(true);
  error = signal<string | null>(null);

  // Pago actions
  procesandoPago = signal<boolean>(false);
  pagoCompletado = signal<boolean>(false);

  // Horas requeridas para el bono
  horasRequeridas = computed(() => {
    const res = this.reserva();
    if (!res) return 0;
    return this.calcularHoras(res.horaInicio, res.horaFin);
  });

  // Saldo de bono disponible para el usuario
  saldoHoras = this.bonoService.saldoHorasBono;

  // Si tiene bono con saldo suficiente
  tieneSaldoSuficiente = computed(() => {
    return this.saldoHoras() >= this.horasRequeridas();
  });

  ngOnInit() {
    const idParam = this.route.snapshot.paramMap.get('reservaId');
    if (idParam) {
      this.reservaId = Number(idParam);
      this.cargarDatos();
    } else {
      this.error.set('ID de reserva no válido.');
      this.cargando.set(false);
    }
  }

  cargarDatos() {
    if (!this.reservaId) return;

    this.cargando.set(true);
    this.error.set(null);

    // Cargar reserva y saldo de bonos en paralelo
    this.reservaService.getReserva(this.reservaId).subscribe({
      next: (res) => {
        this.reserva.set(res);
        if (res.estadoReserva !== 'PENDIENTE_PAGO') {
          this.error.set('La reserva ya ha sido procesada.');
          this.cargando.set(false);
          return;
        }
        
        this.bonoService.cargarMisBonos().subscribe({
          next: () => {
            this.cargando.set(false);
          },
          error: () => {
            // Aún si falla bono, dejamos ver reserva y pagar con MP
            this.cargando.set(false);
          }
        });
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Error al cargar los detalles de la reserva.');
        this.cargando.set(false);
      }
    });
  }

  pagarConMercadoPago() {
    if (!this.reservaId) return;

    this.procesandoPago.set(true);
    this.error.set(null);

    this.pagoService.crearPreferencia(this.reservaId).subscribe({
      next: (response) => {
        // Redirigir a MercadoPago
        window.location.href = response.initPoint;
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Error al conectar con la pasarela de MercadoPago.');
        this.procesandoPago.set(false);
      }
    });
  }

  pagarConBono() {
    if (!this.reservaId) return;

    this.procesandoPago.set(true);
    this.error.set(null);

    this.bonoService.usarBono(this.reservaId).subscribe({
      next: () => {
        this.procesandoPago.set(false);
        this.pagoCompletado.set(true);
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Error al procesar el pago por bono.');
        this.procesandoPago.set(false);
      }
    });
  }

  volverAMisReservas() {
    this.router.navigate(['/reservas/mis-reservas']);
  }

  private calcularHoras(horaInicio: string, horaFin: string): number {
    const [h1, m1] = horaInicio.split(':').map(Number);
    const [h2, m2] = horaFin.split(':').map(Number);
    const totalMinutos = (h2 * 60 + m2) - (h1 * 60 + m1);
    return Math.ceil(totalMinutos / 60);
  }
}
