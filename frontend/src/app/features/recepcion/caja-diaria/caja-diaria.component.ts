import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CajaService } from '../../../core/services/caja.service';
import { ReservaService } from '../../../core/services/reserva.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { CajaDiariaResponse, MovimientoCaja, PendienteCobro } from '../../../shared/models/caja.model';
import { ConceptoPago, MetodoMostrador, MetodoPago } from '../../../shared/models/pago.model';
import { CobroMostradorComponent } from '../../../shared/components/cobro-mostrador/cobro-mostrador.component';
import { formatearPesos } from '../../../shared/utils/moneda.util';
import { todayIsoDate } from '../../../shared/utils/date.util';

const ETIQUETA_METODO: Record<MetodoPago, string> = {
  EFECTIVO: 'Efectivo',
  TARJETA: 'Tarjeta',
  MERCADOPAGO_POINT: 'MercadoPago Point',
  MERCADOPAGO: 'MercadoPago online',
  BONO: 'Horas de bono'
};

const ETIQUETA_CONCEPTO: Record<ConceptoPago, string> = {
  TURNO: 'Turno',
  CONSUMOS: 'Consumos',
  BONO: 'Venta de bono'
};

@Component({
  selector: 'app-caja-diaria',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, CobroMostradorComponent],
  templateUrl: './caja-diaria.component.html'
})
export class CajaDiariaComponent implements OnInit {
  private cajaService = inject(CajaService);
  private reservaService = inject(ReservaService);
  private authService = inject(AuthService);
  private toast = inject(ToastService);

  readonly formatearPesos = formatearPesos;
  readonly hoy = todayIsoDate();

  fecha = signal<string>(this.hoy);
  caja = signal<CajaDiariaResponse | null>(null);
  cargando = signal<boolean>(true);
  ultimaActualizacion = signal<string>('');

  // Arqueo: lo que el staff contó en el cajón contra lo que dice el sistema.
  efectivoContado = signal<number | null>(null);
  diferencia = computed(() => {
    const contado = this.efectivoContado();
    const caja = this.caja();
    if (contado == null || !caja) return null;
    return Math.round((contado - caja.totalEfectivo) * 100) / 100;
  });

  totalPendiente = computed(() => (this.caja()?.pendientesDeCobro ?? []).reduce((s, p) => s + p.monto, 0));
  cantidadIngresos = computed(() => (this.caja()?.movimientos ?? []).filter(m => m.ingreso).length);
  esHoy = computed(() => this.fecha() === this.hoy);
  volverA = computed(() => this.authService.userRole() === 'ADMIN' ? '/admin' : '/dashboard');

  // Cobro de un turno pendiente desde la caja
  pendienteACobrar = signal<PendienteCobro | null>(null);
  cobrando = signal<boolean>(false);

  constructor() {
    // Con la caja de hoy abierta, se refresca sola: los cobros del mostrador y los pagos online van entrando.
    const reloj = setInterval(() => {
      if (this.esHoy() && !this.pendienteACobrar()) this.cargar(true);
    }, 60_000);
    inject(DestroyRef).onDestroy(() => clearInterval(reloj));
  }

  ngOnInit(): void {
    this.cargar();
  }

  cambiarFecha(fecha: string): void {
    if (!fecha) return;
    this.fecha.set(fecha);
    this.efectivoContado.set(null);
    this.cargar();
  }

  irAHoy(): void {
    this.cambiarFecha(this.hoy);
  }

  cargar(silencioso = false): void {
    const fecha = this.fecha();
    if (!silencioso) this.cargando.set(true);
    this.cajaService.obtenerCajaDiaria(fecha).subscribe({
      next: (caja) => {
        // Si mientras tanto se eligió otra fecha, esta respuesta quedó vieja.
        if (fecha !== this.fecha()) return;
        this.caja.set(caja);
        this.cargando.set(false);
        this.ultimaActualizacion.set(new Date().toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' }));
      },
      error: (err) => {
        this.cargando.set(false);
        if (!silencioso) this.toast.error(err.error?.message || 'No se pudo cargar la caja.');
      }
    });
  }

  cobrar(metodo: MetodoMostrador): void {
    const pendiente = this.pendienteACobrar();
    if (!pendiente) return;
    this.cobrando.set(true);
    this.reservaService.cobrarEnMostrador(pendiente.reservaId, metodo).subscribe({
      next: (pago) => {
        this.cobrando.set(false);
        this.pendienteACobrar.set(null);
        this.toast.success(`Cobro registrado: ${formatearPesos(pago.monto)} ${metodo === 'EFECTIVO' ? 'en efectivo' : 'con tarjeta'}.`);
        this.cargar(true);
      },
      error: (err) => {
        this.cobrando.set(false);
        this.toast.error(err.error?.message || 'No se pudo registrar el cobro.');
        // Puede que ya lo haya cobrado otra persona: se refresca la lista.
        this.pendienteACobrar.set(null);
        this.cargar(true);
      }
    });
  }

  etiquetaMetodo(metodo: MetodoPago): string {
    return ETIQUETA_METODO[metodo] ?? metodo;
  }

  etiquetaConcepto(concepto: ConceptoPago): string {
    return ETIQUETA_CONCEPTO[concepto] ?? concepto;
  }

  claseConcepto(concepto: ConceptoPago): string {
    switch (concepto) {
      case 'CONSUMOS': return 'bg-turquoise-500/10 text-turquoise-300 border-turquoise-500/20';
      case 'BONO': return 'bg-accent-500/10 text-accent-300 border-accent-500/20';
      default: return 'bg-primary-500/10 text-primary-300 border-primary-500/20';
    }
  }

  hora(movimiento: MovimientoCaja): string {
    return movimiento.fechaHora?.substring(11, 16) ?? '';
  }

  /** "Sábado 18/10" para el encabezado, sin depender del locale de Angular. */
  fechaLarga(fechaIso: string): string {
    const [anio, mes, dia] = fechaIso.split('-').map(Number);
    const fecha = new Date(anio, mes - 1, dia);
    const dias = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
    return `${dias[fecha.getDay()]} ${String(dia).padStart(2, '0')}/${String(mes).padStart(2, '0')}`;
  }

  porPago(_: number, m: MovimientoCaja): number {
    return m.pagoId;
  }

  porReserva(_: number, p: PendienteCobro): number {
    return p.reservaId;
  }
}
