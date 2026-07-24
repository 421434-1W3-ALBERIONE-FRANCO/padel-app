import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ComisionService } from '../../../core/services/comision.service';
import { UsuarioService } from '../../../core/services/usuario.service';
import { UsuarioResponse } from '../../../shared/models/usuario.model';
import { ReporteComisionesResponse } from '../../../shared/models/comision.model';
import { UiCardComponent } from '../../../shared/components/ui-card/ui-card.component';
import { todayIsoDate } from '../../../shared/utils/date.util';

@Component({
  selector: 'app-comisiones-reporte',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, UiCardComponent],
  templateUrl: './comisiones-reporte.component.html'
})
export class ComisionesReporteComponent implements OnInit {
  private comisionService = inject(ComisionService);
  private usuarioService = inject(UsuarioService);

  recepcionistas = signal<UsuarioResponse[]>([]);
  reporte = signal<ReporteComisionesResponse | null>(null);
  cargando = signal<boolean>(false);
  error = signal<string | null>(null);

  recepcionistaId: number | null = null;
  desde = '';
  hasta = todayIsoDate();

  ngOnInit(): void {
    // Por defecto, el primer día del mes en curso.
    const hoy = new Date();
    this.desde = new Date(hoy.getFullYear(), hoy.getMonth(), 1).toISOString().substring(0, 10);

    this.usuarioService.listarPorRol('RECEPCIONISTA').subscribe({
      next: (data) => this.recepcionistas.set(data),
      error: () => this.error.set('No se pudieron cargar los recepcionistas.')
    });
  }

  generarReporte(): void {
    if (!this.recepcionistaId || !this.desde || !this.hasta) {
      this.error.set('Seleccioná un recepcionista y un rango de fechas.');
      return;
    }

    this.cargando.set(true);
    this.error.set(null);
    this.reporte.set(null);

    this.comisionService.obtenerReporte(this.recepcionistaId, this.desde, this.hasta).subscribe({
      next: (data) => {
        this.reporte.set(data);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Error al generar el reporte.');
        this.cargando.set(false);
      }
    });
  }
}
