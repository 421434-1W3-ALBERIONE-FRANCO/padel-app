import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ReservaService } from '../../../core/services/reserva.service';
import { ToastService } from '../../../core/services/toast.service';
import { ReservaResponse } from '../../../shared/models/reserva.model';

interface QuickCard {
  label: string;
  desc: string;
  route: string;
  icon: string;
  disabled?: boolean;
}

type Rango = 'hoy' | 'semana' | 'mes';

interface BarraGrafico {
  fecha: string;
  etiqueta: string;
  cantidad: number;
  altura: number; // % relativo al máximo
}

@Component({
  selector: 'app-admin-overview',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './admin-overview.component.html'
})
export class AdminOverviewComponent implements OnInit {
  private authService = inject(AuthService);
  private reservaService = inject(ReservaService);
  private toast = inject(ToastService);

  currentUser = this.authService.currentUser;

  reservas = signal<ReservaResponse[]>([]);
  cargando = signal<boolean>(true);
  rango = signal<Rango>('hoy');

  ngOnInit(): void {
    this.reservaService.getAllReservas().subscribe({
      next: (data) => {
        this.reservas.set(data);
        this.cargando.set(false);
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'No se pudieron cargar las métricas.');
        this.cargando.set(false);
      }
    });
  }

  setRango(r: Rango): void {
    this.rango.set(r);
  }

  // --- Rango de fechas (strings ISO YYYY-MM-DD, comparables lexicográficamente) ---
  private rangoFechas = computed<{ desde: string; hasta: string; dias: number }>(() => {
    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    const dias = this.rango() === 'hoy' ? 1 : this.rango() === 'semana' ? 7 : 30;
    const desde = new Date(hoy);
    desde.setDate(hoy.getDate() - (dias - 1));
    return { desde: this.iso(desde), hasta: this.iso(hoy), dias };
  });

  private iso(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private filtradas = computed(() => {
    const { desde, hasta } = this.rangoFechas();
    return this.reservas().filter(r => r.fecha >= desde && r.fecha <= hasta);
  });

  private esConfirmada = (r: ReservaResponse) =>
    r.estadoReserva === 'CONFIRMADA' || r.estadoReserva === 'COMPLETADA';

  totalReservas = computed(() => this.filtradas().length);
  confirmadas = computed(() => this.filtradas().filter(this.esConfirmada).length);
  canceladas = computed(() => this.filtradas().filter(r => r.estadoReserva === 'CANCELADA').length);
  ingresos = computed(() =>
    this.filtradas().filter(this.esConfirmada).reduce((s, r) => s + (r.precioTotal ?? 0), 0)
  );
  tasaConfirmacion = computed(() => {
    const total = this.totalReservas();
    return total === 0 ? 0 : Math.round((this.confirmadas() / total) * 100);
  });

  // Serie de reservas por día dentro del rango, para el gráfico de barras.
  grafico = computed<BarraGrafico[]>(() => {
    const { desde, dias } = this.rangoFechas();
    const base = new Date(desde + 'T00:00:00');
    const conteos: BarraGrafico[] = [];
    for (let i = 0; i < dias; i++) {
      const d = new Date(base);
      d.setDate(base.getDate() + i);
      const fecha = this.iso(d);
      const cantidad = this.reservas().filter(r => r.fecha === fecha).length;
      conteos.push({
        fecha,
        etiqueta: dias <= 7
          ? d.toLocaleDateString('es-AR', { weekday: 'short' })
          : String(d.getDate()),
        cantidad,
        altura: 0
      });
    }
    const max = Math.max(1, ...conteos.map(c => c.cantidad));
    return conteos.map(c => ({ ...c, altura: Math.round((c.cantidad / max) * 100) }));
  });

  readonly cards: QuickCard[] = [
    { label: 'Canchas y Franjas', desc: 'Alta, baja y edición de canchas, horarios y bloqueos.', route: '/admin/canchas', icon: 'M3.75 21h16.5M4.5 3h15M5.25 3v18m13.5-18v18' },
    { label: 'Productos y Stock', desc: 'Catálogo de la tienda y control de inventario.', route: '/admin/productos', icon: 'M20.25 7.5 12 12m0 0L3.75 7.5M12 12v9' },
    { label: 'Control Horario', desc: 'Turnos, sesiones de trabajo y liquidación del staff.', route: '/admin/control-horario', icon: 'M12 6v6h4.5m4.5 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z' },
    { label: 'Comisiones', desc: 'Configuración y reporte de comisiones del personal.', route: '/admin/comisiones', icon: 'M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 0 1 3 19.875v-6.75Z' },
    { label: 'Configuración', desc: 'Parámetros globales del sistema.', route: '/admin/configuracion', icon: 'M4.5 12a7.5 7.5 0 0 0 15 0m-15 0a7.5 7.5 0 1 1 15 0m-15 0H3m16.5 0H21' },
  ];
}
