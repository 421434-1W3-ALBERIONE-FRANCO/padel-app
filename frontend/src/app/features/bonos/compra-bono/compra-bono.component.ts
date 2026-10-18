import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { BonoService } from '../../../core/services/bono.service';
import { AuthService } from '../../../core/services/auth.service';
import { maxIsoDate, toIsoDate } from '../../../shared/utils/date.util';
import { ToastService } from '../../../core/services/toast.service';
import { MetodoMostrador } from '../../../shared/models/pago.model';
import { formatearPesos } from '../../../shared/utils/moneda.util';

@Component({
  selector: 'app-compra-bono',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './compra-bono.component.html',
  styleUrls: ['./compra-bono.component.css']
})
export class CompraBonoComponent implements OnInit {
  private bonoService = inject(BonoService);
  private toast = inject(ToastService);
  private authService = inject(AuthService);
  private router = inject(Router);
  puedeVenderBonos = signal<boolean>(false);
  cargando = signal<boolean>(false);
  exito = signal<boolean>(false);
  // Form fields for Admin assignment
  usuarioEmail = '';
  packSeleccionado = '10HS';
  precioPagado = 8000;
  horasTotales = 10;
  fechaVencimiento = '';
  // Cómo se cobró la venta: queda como pago en la caja del día (no aplica a un bono sin cargo).
  metodoCobro: MetodoMostrador = 'EFECTIVO';
  readonly metodosCobro: { valor: MetodoMostrador; etiqueta: string }[] = [
    { valor: 'EFECTIVO', etiqueta: 'Efectivo' },
    { valor: 'TARJETA', etiqueta: 'Tarjeta' }
  ];
  // El vencimiento debe ser estrictamente posterior a hoy (el día en que se
  // asigna el bono) y el año no puede irse de un rango razonable (evita typos
  // como "20260" en vez de "2026" en el input de fecha).
  private readonly manana = (() => { const d = new Date(); d.setDate(d.getDate() + 1); return d; })();
  fechaVencimientoMin = toIsoDate(this.manana);
  fechaVencimientoMax = maxIsoDate();
  // Standard Packs definition
  packs = [
    { tipo: '10HS', horas: 10, precio: 8000, descripcion: 'Pack inicial. Ideal para jugar un partido semanal.' },
    { tipo: '20HS', horas: 20, precio: 15000, descripcion: 'Pack Pro. Ideal para fanáticos que juegan varias veces por semana.' },
    { tipo: '50HS', horas: 50, precio: 35000, descripcion: 'Pack Club. La mejor tarifa por hora para entrenamiento intensivo.' }
  ];
  ngOnInit() {
    const rol = this.authService.userRole();
    this.puedeVenderBonos.set(rol === 'ADMIN' || rol === 'RECEPCIONISTA');    
    // Set default expiration date to 30 days from now
    const expiry = new Date();
    expiry.setDate(expiry.getDate() + 30);
    this.fechaVencimiento = toIsoDate(expiry);
  }
  onPackChange(packTipo: string) {
    const selected = this.packs.find(p => p.tipo === packTipo);
    if (selected) {
      this.precioPagado = selected.precio;
      this.horasTotales = selected.horas;
    }
  }
  asignarBono() {
    // El precio puede ser 0 (bono sin cargo); lo que no puede es faltar ni ser negativo.
    if (!this.usuarioEmail || !this.packSeleccionado || this.precioPagado == null || !this.horasTotales || !this.fechaVencimiento) {
      this.toast.error('Todos los campos son obligatorios.');
      return;
    }
    if (this.precioPagado < 0) {
      this.toast.error('El precio no puede ser negativo.');
      return;
    }
    if (this.fechaVencimiento < this.fechaVencimientoMin || this.fechaVencimiento > this.fechaVencimientoMax) {
      this.toast.error(`La fecha de vencimiento debe estar entre ${this.fechaVencimientoMin} y ${this.fechaVencimientoMax}.`);
      return;
    }
    this.cargando.set(true);
    this.exito.set(false);
    this.bonoService.asignarBono({
      usuarioEmail: this.usuarioEmail,
      tipo: this.packSeleccionado,
      horasTotales: this.horasTotales,
      precioPagado: this.precioPagado,
      fechaVencimiento: this.fechaVencimiento,
      metodo: this.precioPagado > 0 ? this.metodoCobro : null
    }).subscribe({
      next: () => {
        this.cargando.set(false);
        this.exito.set(true);
        this.toast.success(this.precioPagado > 0
          ? `Bono asignado. Venta de ${formatearPesos(this.precioPagado)} registrada ${this.metodoCobro === 'EFECTIVO' ? 'en efectivo' : 'con tarjeta'} en la caja.`
          : 'Bono asignado sin cargo.');
        this.usuarioEmail = '';
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Error al asignar el bono al usuario.');
        this.cargando.set(false);
      }
    });
  }
}
