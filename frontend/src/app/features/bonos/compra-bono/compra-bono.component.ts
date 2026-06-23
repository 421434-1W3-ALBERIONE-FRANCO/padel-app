import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { BonoService } from '../../../core/services/bono.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-compra-bono',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './compra-bono.component.html',
  styleUrls: ['./compra-bono.component.css']
})
export class CompraBonoComponent implements OnInit {
  private bonoService = inject(BonoService);
  private authService = inject(AuthService);
  private router = inject(Router);

  isAdmin = signal<boolean>(false);
  cargando = signal<boolean>(false);
  exito = signal<boolean>(false);
  error = signal<string | null>(null);

  // Form fields for Admin assignment
  usuarioEmail = '';
  packSeleccionado = '10HS';
  precioPagado = 8000;
  horasTotales = 10;
  fechaVencimiento = '';

  // Standard Packs definition
  packs = [
    { tipo: '10HS', horas: 10, precio: 8000, descripcion: 'Pack inicial. Ideal para jugar un partido semanal.' },
    { tipo: '20HS', horas: 20, precio: 15000, descripcion: 'Pack Pro. Ideal para fanáticos que juegan varias veces por semana.' },
    { tipo: '50HS', horas: 50, precio: 35000, descripcion: 'Pack Club. La mejor tarifa por hora para entrenamiento intensivo.' }
  ];

  ngOnInit() {
    this.isAdmin.set(this.authService.userRole() === 'ADMIN');
    
    // Set default expiration date to 30 days from now
    const expiry = new Date();
    expiry.setDate(expiry.getDate() + 30);
    this.fechaVencimiento = expiry.toISOString().substring(0, 10);
  }

  onPackChange(packTipo: string) {
    const selected = this.packs.find(p => p.tipo === packTipo);
    if (selected) {
      this.precioPagado = selected.precio;
      this.horasTotales = selected.horas;
    }
  }

  asignarBono() {
    if (!this.usuarioEmail || !this.packSeleccionado || !this.precioPagado || !this.horasTotales || !this.fechaVencimiento) {
      this.error.set('Todos los campos son obligatorios.');
      return;
    }

    this.cargando.set(true);
    this.error.set(null);
    this.exito.set(false);

    this.bonoService.asignarBono({
      usuarioEmail: this.usuarioEmail,
      tipo: this.packSeleccionado,
      horasTotales: this.horasTotales,
      precioPagado: this.precioPagado,
      fechaVencimiento: this.fechaVencimiento
    }).subscribe({
      next: () => {
        this.cargando.set(false);
        this.exito.set(true);
        this.usuarioEmail = '';
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Error al asignar el bono al usuario.');
        this.cargando.set(false);
      }
    });
  }
}
