import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ReporteService } from '../../../core/services/reporte.service';
import { ToastService } from '../../../core/services/toast.service';
import { MetricasReservasResponse } from '../../../shared/models/caja.model';

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
  imports: [CommonModule, RouterLink],
  templateUrl: './admin-overview.component.html'
})
export class AdminOverviewComponent implements OnInit {
  private authService = inject(AuthService);
  private reporteService = inject(ReporteService);
  private toast = inject(ToastService);

  currentUser = this.authService.currentUser;

  // Las cuentas las hace el backend: antes el panel bajaba el historial completo de reservas para contar acá.
  metricas = signal<MetricasReservasResponse | null>(null);
  cargando = signal<boolean>(true);
  rango = signal<Rango>('hoy');

  ngOnInit(): void {
    this.cargarMetricas();
  }

  setRango(r: Rango): void {
    this.rango.set(r);
    this.cargarMetricas();
  }

  private cargarMetricas(): void {
    const { desde, hasta } = this.rangoFechas();
    this.cargando.set(true);
    this.reporteService.metricasReservas(desde, hasta).subscribe({
      next: (data) => {
        // Si mientras tanto se eligió otro rango, esta respuesta quedó vieja.
        if (data.desde !== this.rangoFechas().desde) return;
        this.metricas.set(data);
        this.cargando.set(false);
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'No se pudieron cargar las métricas.');
        this.cargando.set(false);
      }
    });
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

  totalReservas = computed(() => this.metricas()?.totalReservas ?? 0);
  confirmadas = computed(() => this.metricas()?.confirmadas ?? 0);
  canceladas = computed(() => this.metricas()?.canceladas ?? 0);
  ingresos = computed(() => this.metricas()?.ingresos ?? 0);
  tasaConfirmacion = computed(() => this.metricas()?.tasaConfirmacion ?? 0);

  // Serie de reservas por día dentro del rango, para el gráfico de barras.
  grafico = computed<BarraGrafico[]>(() => {
    const { desde, dias } = this.rangoFechas();
    const base = new Date(desde + 'T00:00:00');
    // El backend solo informa los días con reservas: el resto va en cero.
    const porDia = new Map((this.metricas()?.porDia ?? []).map(d => [d.fecha, d.cantidad]));
    const conteos: BarraGrafico[] = [];
    for (let i = 0; i < dias; i++) {
      const d = new Date(base);
      d.setDate(base.getDate() + i);
      const fecha = this.iso(d);
      const cantidad = porDia.get(fecha) ?? 0;
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
    { label: 'Reservas globales', desc: 'Ver y cancelar cualquier reserva del sistema.', route: '/admin/reservas', icon: 'M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 0 1 2.25-2.25h13.5A2.25 2.25 0 0 1 21 7.5v11.25' },
    { label: 'Ocupación en vivo', desc: 'Estado de las canchas por franja, en tiempo real.', route: '/admin/ocupacion', icon: 'M2.25 13.5h3.86a2.25 2.25 0 0 1 2.012 1.244l.256.512a2.25 2.25 0 0 0 2.013 1.244h3.218a2.25 2.25 0 0 0 2.013-1.244l.256-.512a2.25 2.25 0 0 1 2.013-1.244h3.859' },
    { label: 'Gestión de Staff', desc: 'Alta y listado de recepcionistas y administradores.', route: '/admin/staff', icon: 'M15 19.128a9.38 9.38 0 0 0 2.625.372 9.337 9.337 0 0 0 4.121-.952 4.125 4.125 0 0 0-7.533-2.493' },
    { label: 'Canchas y Franjas', desc: 'Alta, baja y edición de canchas, horarios y bloqueos.', route: '/admin/canchas', icon: 'M3.75 21h16.5M4.5 3h15M5.25 3v18m13.5-18v18' },
    { label: 'Productos y Stock', desc: 'Catálogo de la tienda y control de inventario.', route: '/admin/productos', icon: 'M20.25 7.5 12 12m0 0L3.75 7.5M12 12v9' },
    { label: 'Catálogo de bonos', desc: 'Packs de horas que se venden: horas, precio y vigencia.', route: '/admin/tipos-bono', icon: 'M16.5 6v.75m0 3v.75m0 3v.75m0 3V18m-9-5.25h5.25M7.5 15h3M3.375 5.25c-.621 0-1.125.504-1.125 1.125v3.026a2.999 2.999 0 0 1 0 5.198v3.026c0 .621.504 1.125 1.125 1.125h17.25c.621 0 1.125-.504 1.125-1.125v-3.026a2.999 2.999 0 0 1 0-5.198V6.375c0-.621-.504-1.125-1.125-1.125H3.375Z' },
    { label: 'Control Horario', desc: 'Turnos, sesiones de trabajo y liquidación del staff.', route: '/admin/control-horario', icon: 'M12 6v6h4.5m4.5 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z' },
    { label: 'Comisiones', desc: 'Configuración y reporte de comisiones del personal.', route: '/admin/comisiones', icon: 'M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 0 1 3 19.875v-6.75Z' },
    { label: 'Configuración', desc: 'Parámetros globales del sistema.', route: '/admin/configuracion', icon: 'M4.5 12a7.5 7.5 0 0 0 15 0m-15 0a7.5 7.5 0 1 1 15 0m-15 0H3m16.5 0H21' },
  ];
}
