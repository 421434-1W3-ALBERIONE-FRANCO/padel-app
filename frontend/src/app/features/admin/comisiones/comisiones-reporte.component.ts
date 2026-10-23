import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ComisionService } from '../../../core/services/comision.service';
import { UsuarioService } from '../../../core/services/usuario.service';
import { ConfiguracionService } from '../../../core/services/configuracion.service';
import { ToastService } from '../../../core/services/toast.service';
import { UsuarioResponse } from '../../../shared/models/usuario.model';
import { ReporteComisionesResponse } from '../../../shared/models/comision.model';
import { ConfiguracionResponse } from '../../../shared/models/configuracion.model';
import { UiCardComponent } from '../../../shared/components/ui-card/ui-card.component';
import { firstDayOfMonthIsoDate, todayIsoDate } from '../../../shared/utils/date.util';

@Component({
  selector: 'app-comisiones-reporte',
  imports: [CommonModule, FormsModule, RouterLink, UiCardComponent],
  templateUrl: './comisiones-reporte.component.html'
})
export class ComisionesReporteComponent implements OnInit {
  private comisionService = inject(ComisionService);
  private usuarioService = inject(UsuarioService);
  private configuracionService = inject(ConfiguracionService);
  private toast = inject(ToastService);

  recepcionistas = signal<UsuarioResponse[]>([]);
  reporte = signal<ReporteComisionesResponse | null>(null);
  cargando = signal<boolean>(false);

  recepcionistaId: number | null = null;
  desde = '';
  hasta = todayIsoDate();

  // --- Configuración de comisiones (movida desde Configuración del Sistema) ---
  private configActual: ConfiguracionResponse | null = null;
  comisionesHabilitadas = false;
  porcentajeComisionRecepcion = 0;
  configCargando = signal<boolean>(true);
  configGuardando = signal<boolean>(false);

  ngOnInit(): void {
    // Por defecto, el primer día del mes en curso.
    this.desde = firstDayOfMonthIsoDate();

    this.usuarioService.listarPorRol('RECEPCIONISTA').subscribe({
      next: (data) => this.recepcionistas.set(data),
      error: () => this.toast.error('No se pudieron cargar los recepcionistas.')
    });

    this.cargarConfig();
  }

  cargarConfig(): void {
    this.configCargando.set(true);
    this.configuracionService.obtener().subscribe({
      next: (config) => {
        this.configActual = config;
        this.comisionesHabilitadas = config.comisionesHabilitadas;
        this.porcentajeComisionRecepcion = config.porcentajeComisionRecepcion;
        this.configCargando.set(false);
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'No se pudo cargar la configuración de comisiones.');
        this.configCargando.set(false);
      }
    });
  }

  guardarConfigComisiones(): void {
    if (!this.configActual) return;
    if (this.porcentajeComisionRecepcion < 0 || this.porcentajeComisionRecepcion > 100) {
      this.toast.error('El porcentaje debe estar entre 0 y 100.');
      return;
    }

    this.configGuardando.set(true);

    // Se re-envía la config completa: solo cambian los campos de comisiones,
    // el resto (avisos, control horario) se preserva de lo cargado.
    this.configuracionService.actualizar({
      comisionesHabilitadas: this.comisionesHabilitadas,
      porcentajeComisionRecepcion: this.porcentajeComisionRecepcion,
      diasAvisoVencimientoBono: this.configActual.diasAvisoVencimientoBono,
      horaTopeCierreSesion: this.configActual.horaTopeCierreSesion
    }).subscribe({
      next: (config) => {
        this.configActual = config;
        this.configGuardando.set(false);
        this.toast.success('Configuración de comisiones guardada.');
      },
      error: (err) => {
        this.configGuardando.set(false);
        this.toast.error(err.error?.message || 'Error al guardar la configuración de comisiones.');
      }
    });
  }

  generarReporte(): void {
    if (!this.recepcionistaId || !this.desde || !this.hasta) {
      this.toast.error('Seleccioná un recepcionista y un rango de fechas.');
      return;
    }

    this.cargando.set(true);
    this.reporte.set(null);

    this.comisionService.obtenerReporte(this.recepcionistaId, this.desde, this.hasta).subscribe({
      next: (data) => {
        this.reporte.set(data);
        this.cargando.set(false);
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Error al generar el reporte.');
        this.cargando.set(false);
      }
    });
  }
}
