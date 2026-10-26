import { Component, OnInit, computed, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { BonoService } from '../../../core/services/bono.service';
import { TipoBonoService } from '../../../core/services/tipo-bono.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { MetodoMostrador } from '../../../shared/models/pago.model';
import { TipoBonoResponse } from '../../../shared/models/bono.model';
import { formatearPesos } from '../../../shared/utils/moneda.util';

@Component({
  selector: 'app-compra-bono',
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './compra-bono.component.html',
  styleUrls: ['./compra-bono.component.css']
})
export class CompraBonoComponent implements OnInit {
  private bonoService = inject(BonoService);
  private tipoBonoService = inject(TipoBonoService);
  private toast = inject(ToastService);
  private authService = inject(AuthService);

  puedeVenderBonos = computed(() => {
    const rol = this.authService.userRole();
    return rol === 'ADMIN' || rol === 'RECEPCIONISTA';
  });
  // Regalar un bono es decisión del ADMIN (y queda el motivo): antes cualquiera del staff ponía precio 0.
  puedeRegalar = computed(() => this.authService.userRole() === 'ADMIN');

  // Catálogo: horas, precio y vigencia los define el ADMIN, no quien vende.
  tipos = signal<TipoBonoResponse[]>([]);
  cargandoTipos = signal<boolean>(true);
  errorTipos = signal<string | null>(null);

  cargando = signal<boolean>(false);
  exito = signal<boolean>(false);

  usuarioEmail = '';
  tipoBonoId: number | null = null;
  sinCargo = false;
  motivoSinCargo = '';
  // Cómo se cobró la venta: queda como pago en la caja del día (no aplica a un bono sin cargo).
  metodoCobro: MetodoMostrador = 'EFECTIVO';
  readonly metodosCobro: { valor: MetodoMostrador; etiqueta: string }[] = [
    { valor: 'EFECTIVO', etiqueta: 'Efectivo' },
    { valor: 'TARJETA', etiqueta: 'Tarjeta' }
  ];

  ngOnInit() {
    this.cargarTipos();
  }

  cargarTipos(): void {
    this.cargandoTipos.set(true);
    this.errorTipos.set(null);
    this.tipoBonoService.listar().subscribe({
      next: (tipos) => {
        this.tipos.set(tipos);
        this.tipoBonoId ??= tipos[0]?.id ?? null;
        this.cargandoTipos.set(false);
      },
      error: (err) => {
        this.errorTipos.set(err.error?.message || 'No se pudo cargar el catálogo de bonos.');
        this.cargandoTipos.set(false);
      }
    });
  }

  tipoSeleccionado(): TipoBonoResponse | undefined {
    return this.tipos().find(t => t.id === this.tipoBonoId);
  }

  /** El pack es gratis por catálogo o el ADMIN lo marcó sin cargo: no hay cobro que registrar. */
  esSinCobro(): boolean {
    const tipo = this.tipoSeleccionado();
    return this.sinCargo || (!!tipo && tipo.precio === 0);
  }

  asignarBono() {
    const tipo = this.tipoSeleccionado();
    if (!this.usuarioEmail.trim() || !tipo) {
      this.toast.error('Indicá el email del jugador y el pack.');
      return;
    }
    if (this.sinCargo && !this.motivoSinCargo.trim()) {
      this.toast.error('Indicá el motivo del bono sin cargo.');
      return;
    }
    const cobrado = !this.esSinCobro();
    this.cargando.set(true);
    this.exito.set(false);
    this.bonoService.asignarBono({
      usuarioEmail: this.usuarioEmail.trim(),
      tipoBonoId: tipo.id,
      metodo: cobrado ? this.metodoCobro : null,
      sinCargo: this.sinCargo,
      motivoSinCargo: this.sinCargo ? this.motivoSinCargo.trim() : null
    }).subscribe({
      next: () => {
        this.cargando.set(false);
        this.exito.set(true);
        this.toast.success(cobrado
          ? `Bono asignado. Venta de ${formatearPesos(tipo.precio)} registrada ${this.metodoCobro === 'EFECTIVO' ? 'en efectivo' : 'con tarjeta'} en la caja.`
          : 'Bono asignado sin cargo.');
        this.usuarioEmail = '';
        this.sinCargo = false;
        this.motivoSinCargo = '';
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Error al asignar el bono al usuario.');
        this.cargando.set(false);
      }
    });
  }
}
