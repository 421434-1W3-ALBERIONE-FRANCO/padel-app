import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { DisponibilidadService } from '../../../core/services/disponibilidad.service';
import { CanchaAdminService } from '../../../core/services/cancha-admin.service';
import { ReservaService } from '../../../core/services/reserva.service';
import { UsuarioService } from '../../../core/services/usuario.service';
import { ToastService } from '../../../core/services/toast.service';
import { CanchaResponse } from '../../../shared/models/cancha.model';
import { OpcionDuracion, SlotDisponibilidad } from '../../../shared/models/disponibilidad.model';
import { UsuarioResponse } from '../../../shared/models/usuario.model';

function toIso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

@Component({
  selector: 'app-alta-turno',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './alta-turno.component.html'
})
export class AltaTurnoComponent implements OnInit {
  private disponibilidadService = inject(DisponibilidadService);
  private canchaService = inject(CanchaAdminService);
  private reservaService = inject(ReservaService);
  private usuarioService = inject(UsuarioService);
  private toast = inject(ToastService);

  // Clientes
  clientes = signal<UsuarioResponse[]>([]);
  busqueda = signal<string>('');
  clienteSeleccionado = signal<UsuarioResponse | null>(null);

  clientesFiltrados = computed(() => {
    const q = this.busqueda().trim().toLowerCase();
    if (!q) return this.clientes().slice(0, 8);
    return this.clientes().filter(c =>
      `${c.nombre} ${c.apellido} ${c.email}`.toLowerCase().includes(q)
    ).slice(0, 8);
  });

  // Cancha / fecha / disponibilidad
  canchas = signal<CanchaResponse[]>([]);
  canchaId = signal<number | null>(null);
  fecha = signal<string>(toIso(new Date()));
  fechaMin = toIso(new Date());
  slots = signal<SlotDisponibilidad[]>([]);
  cargando = signal<boolean>(false);

  // Selección de turno
  slotSel = signal<SlotDisponibilidad | null>(null);
  duracionSel = signal<OpcionDuracion | null>(null);
  creando = signal<boolean>(false);

  ngOnInit(): void {
    this.usuarioService.listarPorRol('JUGADOR').subscribe({
      next: (data) => this.clientes.set(data),
      error: (err) => this.toast.error(err.error?.message || 'No se pudieron cargar los clientes.')
    });
    this.canchaService.getCanchas().subscribe({
      next: (data) => {
        const activas = data.filter(c => c.activa);
        this.canchas.set(activas);
        if (activas.length) {
          this.canchaId.set(activas[0].id);
          this.cargarDisponibilidad();
        }
      },
      error: (err) => this.toast.error(err.error?.message || 'No se pudieron cargar las canchas.')
    });
  }

  seleccionarCliente(c: UsuarioResponse): void {
    this.clienteSeleccionado.set(c);
    this.busqueda.set('');
  }

  cambiarCancha(id: number): void {
    this.canchaId.set(id);
    this.slotSel.set(null);
    this.cargarDisponibilidad();
  }

  cambiarFecha(f: string): void {
    this.fecha.set(f);
    this.slotSel.set(null);
    this.cargarDisponibilidad();
  }

  cargarDisponibilidad(): void {
    const id = this.canchaId();
    if (!id) return;
    this.cargando.set(true);
    this.disponibilidadService.getDisponibilidad(id, this.fecha()).subscribe({
      next: (res) => { this.slots.set(res.slots); this.cargando.set(false); },
      error: (err) => { this.toast.error(err.error?.message || 'No se pudo cargar la disponibilidad.'); this.cargando.set(false); }
    });
  }

  seleccionarSlot(slot: SlotDisponibilidad): void {
    if (!slot.disponible) return;
    this.slotSel.set(slot);
    const ops = slot.opcionesDuracion ?? [];
    this.duracionSel.set(ops.find(o => o.duracionMin === 90) ?? ops[0] ?? null);
  }

  elegirDuracion(op: OpcionDuracion): void {
    this.duracionSel.set(op);
  }

  precioSel = computed(() => this.duracionSel()?.precio ?? this.slotSel()?.precio ?? 0);

  confirmar(): void {
    const cliente = this.clienteSeleccionado();
    const slot = this.slotSel();
    const id = this.canchaId();
    if (!cliente) { this.toast.error('Elegí un cliente.'); return; }
    if (!slot || !id) { this.toast.error('Elegí un horario disponible.'); return; }

    this.creando.set(true);
    this.reservaService.crearReserva({
      canchaId: id,
      franjaId: slot.franjaId,
      fecha: this.fecha(),
      origen: 'RECEPCION',
      duracionMin: this.duracionSel()?.duracionMin,
      usuarioId: cliente.id
    }).pipe(
      // El turno dado de alta por recepción se cobra en el club: se confirma en el acto. Si quedara
      // PENDIENTE_PAGO, el scheduler lo cancelaba a los 15 minutos "por falta de pago".
      switchMap(reserva => this.reservaService.confirmarReserva(reserva.id))
    ).subscribe({
      next: () => {
        this.creando.set(false);
        this.toast.success(`Turno confirmado para ${cliente.nombre} ${cliente.apellido}. El cobro se registra en el club.`);
        this.slotSel.set(null);
        this.duracionSel.set(null);
        this.cargarDisponibilidad();
      },
      error: (err) => {
        this.creando.set(false);
        this.toast.error(err.error?.message || 'No se pudo crear el turno.');
        this.cargarDisponibilidad();
      }
    });
  }
}
