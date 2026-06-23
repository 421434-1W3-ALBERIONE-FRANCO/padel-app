import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { BonoService } from '../../../core/services/bono.service';
import { BonoResponse } from '../../../shared/models/bono.model';

@Component({
  selector: 'app-mis-bonos',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './mis-bonos.component.html',
  styleUrls: ['./mis-bonos.component.css']
})
export class MisBonosComponent implements OnInit {
  private bonoService = inject(BonoService);

  bonos = this.bonoService.bonos;
  saldoHoras = this.bonoService.saldoHorasBono;

  cargando = signal<boolean>(true);
  error = signal<string | null>(null);

  ngOnInit() {
    this.cargarDatos();
  }

  cargarDatos() {
    this.cargando.set(true);
    this.error.set(null);

    this.bonoService.cargarMisBonos().subscribe({
      next: () => {
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Error al obtener tus bonos prepago.');
        this.cargando.set(false);
      }
    });
  }

  obtenerClaseEstado(estado: string): string {
    switch (estado) {
      case 'ACTIVO':
        return 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20';
      case 'AGOTADO':
        return 'bg-slate-800 text-slate-400 border border-slate-700';
      case 'VENCIDO':
        return 'bg-red-500/10 text-red-400 border border-red-500/20';
      default:
        return 'bg-slate-800 text-slate-400';
    }
  }
}
