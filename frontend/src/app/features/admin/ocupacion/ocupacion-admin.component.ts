import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { CanchaAdminService } from '../../../core/services/cancha-admin.service';
import { ReservaService } from '../../../core/services/reserva.service';
import { ToastService } from '../../../core/services/toast.service';
import { CanchaResponse } from '../../../shared/models/cancha.model';
import { FranjaHorariaResponse } from '../../../shared/models/franja.model';
import { ReservaResponse } from '../../../shared/models/reserva.model';

interface CanchaConFranjas {
  cancha: CanchaResponse;
  franjas: FranjaHorariaResponse[];
}

interface Celda {
  horaInicio: string;
  horaFin: string;
  ocupada: boolean;
}

interface FilaCancha {
  nombre: string;
  celdas: Celda[];
  ocupadas: number;
  total: number;
}

@Component({
  selector: 'app-ocupacion-admin',
  imports: [CommonModule, FormsModule],
  templateUrl: './ocupacion-admin.component.html'
})
export class OcupacionAdminComponent implements OnInit, OnDestroy {
  private canchaService = inject(CanchaAdminService);
  private reservaService = inject(ReservaService);
  private toast = inject(ToastService);

  private readonly REFRESH_MS = 15000;
  private timer?: ReturnType<typeof setInterval>;

  base = signal<CanchaConFranjas[]>([]);
  reservas = signal<ReservaResponse[]>([]);
  cargando = signal<boolean>(true);
  fecha = signal<string>(this.hoyIso());
  ultimaActualizacion = signal<string>('');

  private hoyIso(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private ocupa(estado: string): boolean {
    return estado === 'CONFIRMADA' || estado === 'PENDIENTE_PAGO' || estado === 'COMPLETADA';
  }

  private static readonly DIAS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

  grid = computed<FilaCancha[]>(() => {
    const fecha = this.fecha();
    const diaSemana = OcupacionAdminComponent.DIAS[new Date(`${fecha}T00:00:00`).getDay()];
    // El backend ya filtra por día, pero al cambiar la fecha la lista sigue siendo la anterior hasta que llega la nueva.
    const reservas = this.reservas().filter(r => r.fecha === fecha && this.ocupa(r.estadoReserva));
    const hhmm = (t: string) => t.substring(0, 5);
    return this.base().map(({ cancha, franjas }) => {
      // Solo las franjas que aplican al día elegido (una cancha puede tener horarios distintos
      // entre semana y el fin de semana), y una franja está ocupada si alguna reserva se superpone:
      // con duración variable una reserva larga cubre también las franjas siguientes.
      const celdas = franjas
        .filter(f => f.diasAplicables.includes(diaSemana))
        .sort((a, b) => a.horaInicio.localeCompare(b.horaInicio))
        .map(f => ({
          horaInicio: f.horaInicio,
          horaFin: f.horaFin,
          ocupada: reservas.some(r => r.canchaId === cancha.id
            && hhmm(r.horaInicio) < hhmm(f.horaFin)
            && hhmm(f.horaInicio) < hhmm(r.horaFin))
        }));
      return {
        nombre: cancha.nombre,
        celdas,
        ocupadas: celdas.filter(c => c.ocupada).length,
        total: celdas.length
      };
    });
  });

  totalSlots = computed(() => this.grid().reduce((s, f) => s + f.total, 0));
  totalOcupados = computed(() => this.grid().reduce((s, f) => s + f.ocupadas, 0));
  porcentajeGlobal = computed(() => {
    const t = this.totalSlots();
    return t === 0 ? 0 : Math.round((this.totalOcupados() / t) * 100);
  });

  ngOnInit(): void {
    this.cargarBase();
    this.timer = setInterval(() => this.refrescarReservas(true), this.REFRESH_MS);
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  private cargarBase(): void {
    this.cargando.set(true);
    this.canchaService.getCanchas().subscribe({
      next: (canchas) => {
        const activas = canchas.filter(c => c.activa);
        if (activas.length === 0) {
          this.base.set([]);
          this.cargando.set(false);
          this.refrescarReservas(false);
          return;
        }
        forkJoin(
          activas.map(c => this.canchaService.getFranjas(c.id).pipe(catchError(() => of([] as FranjaHorariaResponse[]))))
        ).subscribe(franjasPorCancha => {
          this.base.set(activas.map((cancha, i) => ({ cancha, franjas: franjasPorCancha[i] })));
          this.cargando.set(false);
          this.refrescarReservas(false);
        });
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'No se pudo cargar la ocupación.');
        this.cargando.set(false);
      }
    });
  }

  cambiarFecha(fecha: string): void {
    this.fecha.set(fecha);
    this.refrescarReservas(false);
  }

  refrescarReservas(silencioso: boolean): void {
    // Agenda del día elegido (input vacío -> no se consulta).
    const fecha = this.fecha();
    if (!fecha) return;
    this.reservaService.getAgenda(fecha).subscribe({
      next: (data) => {
        // Si mientras tanto se eligió otra fecha, esta respuesta quedó vieja (la reemplaza la de la fecha nueva).
        if (fecha !== this.fecha()) return;
        this.reservas.set(data);
        this.ultimaActualizacion.set(new Date().toLocaleTimeString('es-AR'));
      },
      error: () => { if (!silencioso) this.toast.error('No se pudieron actualizar las reservas.'); }
    });
  }
}
