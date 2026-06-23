import { Component, OnInit, OnDestroy, signal, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { DisponibilidadService } from '../../../core/services/disponibilidad.service';
import { CanchaAdminService } from '../../../core/services/cancha-admin.service';
import { ReservaService } from '../../../core/services/reserva.service';
import { BonoService } from '../../../core/services/bono.service';
import { CanchaResponse } from '../../../shared/models/cancha.model';
import { SlotDisponibilidad } from '../../../shared/models/disponibilidad.model';

import { RouterLink } from '@angular/router';
import { UiCardComponent } from '../../../shared/components/ui-card/ui-card.component';

@Component({
  selector: 'app-calendario',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, UiCardComponent],
  templateUrl: './calendario.component.html',
  styleUrls: ['./calendario.component.css']
})
export class CalendarioComponent implements OnInit, OnDestroy {
  private disponibilidadService = inject(DisponibilidadService);
  private canchaAdminService = inject(CanchaAdminService);
  private reservaService = inject(ReservaService);
  private bonoService = inject(BonoService);
  private router = inject(Router);

  canchas = signal<CanchaResponse[]>([]);
  canchaSeleccionadaId = signal<number | null>(null);
  fechaSeleccionada = signal<string>(new Date().toISOString().substring(0, 10)); // YYYY-MM-DD
  slots = signal<SlotDisponibilidad[]>([]);

  cargando = signal<boolean>(false);
  mensajeError = signal<string | null>(null);

  // Estados para el Modal de Reserva
  slotParaReservar = signal<SlotDisponibilidad | null>(null);
  cargandoReserva = signal<boolean>(false);
  reservaExitosa = signal<boolean>(false);
  errorReserva = signal<string | null>(null);
  reservaCreadaId = signal<number | null>(null);
  pagadoConBono = signal<boolean>(false);

  // Signals para Bonos
  saldoHoras = this.bonoService.saldoHorasBono;
  horasRequeridas = computed(() => {
    const slot = this.slotParaReservar();
    return slot ? Math.ceil(slot.duracionMin / 60) : 0;
  });
  tieneSaldoBono = computed(() => {
    return this.saldoHoras() >= this.horasRequeridas();
  });

  private wsSubscription: Subscription | null = null;

  ngOnInit() {
    this.cargarCanchas();
    this.suscribirActualizacionesWS();
    this.bonoService.cargarMisBonos().subscribe({
      error: () => console.warn('Could not load user coupon packs balance in Calendar.')
    });
  }

  ngOnDestroy() {
    this.wsSubscription?.unsubscribe();
  }

  cargarCanchas() {
    this.cargando.set(true);
    this.canchaAdminService.getCanchas().subscribe({
      next: (data) => {
        const activas = data.filter(c => c.activa);
        this.canchas.set(activas);
        if (activas.length > 0) {
          this.canchaSeleccionadaId.set(activas[0].id);
          this.cargarDisponibilidad();
        } else {
          this.cargando.set(false);
        }
      },
      error: () => {
        this.mensajeError.set('Error al cargar las canchas.');
        this.cargando.set(false);
      }
    });
  }

  cargarDisponibilidad() {
    const canchaId = this.canchaSeleccionadaId();
    const fecha = this.fechaSeleccionada();

    if (!canchaId || !fecha) return;

    this.cargando.set(true);
    this.mensajeError.set(null);
    this.disponibilidadService.getDisponibilidad(canchaId, fecha).subscribe({
      next: (res) => {
        this.slots.set(res.slots);
        this.cargando.set(false);
      },
      error: (err) => {
        this.mensajeError.set(err.error?.message || 'Error al obtener la disponibilidad de la cancha.');
        this.cargando.set(false);
      }
    });
  }

  onCanchaChange(canchaId: number) {
    this.canchaSeleccionadaId.set(canchaId);
    this.cargarDisponibilidad();
  }

  onFechaChange(fecha: string) {
    this.fechaSeleccionada.set(fecha);
    this.cargarDisponibilidad();
  }

  seleccionarSlot(slot: SlotDisponibilidad) {
    if (!slot.disponible) return;
    this.slotParaReservar.set(slot);
    this.errorReserva.set(null);
    this.reservaExitosa.set(false);
    this.reservaCreadaId.set(null);
    this.pagadoConBono.set(false);
  }

  cerrarModal() {
    this.slotParaReservar.set(null);
    this.reservaExitosa.set(false);
    this.errorReserva.set(null);
    this.reservaCreadaId.set(null);
    this.cargarDisponibilidad();
  }

  confirmarReserva() {
    const slot = this.slotParaReservar();
    const canchaId = this.canchaSeleccionadaId();
    const fecha = this.fechaSeleccionada();

    if (!slot || !canchaId || !fecha) return;

    this.cargandoReserva.set(true);
    this.errorReserva.set(null);

    this.reservaService.crearReserva({
      canchaId,
      franjaId: slot.franjaId,
      fecha,
      origen: 'APP'
    }).subscribe({
      next: (res) => {
        this.cargandoReserva.set(false);
        this.reservaCreadaId.set(res.id);
        this.reservaExitosa.set(true);
      },
      error: (err) => {
        this.cargandoReserva.set(false);
        this.errorReserva.set(err.error?.message || 'Error al procesar la reserva. Por favor intente nuevamente.');
      }
    });
  }

  confirmarYReservarConBono() {
    const slot = this.slotParaReservar();
    const canchaId = this.canchaSeleccionadaId();
    const fecha = this.fechaSeleccionada();

    if (!slot || !canchaId || !fecha) return;

    this.cargandoReserva.set(true);
    this.errorReserva.set(null);

    // 1. Crear la reserva
    this.reservaService.crearReserva({
      canchaId,
      franjaId: slot.franjaId,
      fecha,
      origen: 'APP'
    }).subscribe({
      next: (res) => {
        this.reservaCreadaId.set(res.id);
        // 2. Cobrar con bono inmediatamente
        this.bonoService.usarBono(res.id).subscribe({
          next: () => {
            this.cargandoReserva.set(false);
            this.pagadoConBono.set(true);
            this.reservaExitosa.set(true);
          },
          error: (err) => {
            this.cargandoReserva.set(false);
            this.errorReserva.set(err.error?.message || 'La reserva fue creada pendiente de pago, pero no se pudo aplicar el bono.');
          }
        });
      },
      error: (err) => {
        this.cargandoReserva.set(false);
        this.errorReserva.set(err.error?.message || 'Error al procesar la reserva. Por favor intente nuevamente.');
      }
    });
  }

  irAMisReservas() {
    this.slotParaReservar.set(null);
    this.router.navigate(['/reservas/mis-reservas']);
  }

  obtenerNombreCanchaSeleccionada(): string {
    const id = this.canchaSeleccionadaId();
    const cancha = this.canchas().find(c => c.id === id);
    return cancha ? cancha.nombre : '';
  }

  private suscribirActualizacionesWS() {
    this.wsSubscription = this.disponibilidadService.updates$.subscribe((payload) => {
      if (payload && payload.canchaId === this.canchaSeleccionadaId()) {
        console.log('WS Update received for current court, refreshing grid...');
        this.cargarDisponibilidad();
      }
    });
  }
}
