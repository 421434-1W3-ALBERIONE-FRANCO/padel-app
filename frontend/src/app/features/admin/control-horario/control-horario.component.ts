import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { UsuarioService } from '../../../core/services/usuario.service';
import { ControlHorarioService } from '../../../core/services/control-horario.service';
import { UsuarioResponse } from '../../../shared/models/usuario.model';
import {
  BancoHorasResponse,
  DiaSemana,
  LiquidacionResponse,
  TurnoResponse
} from '../../../shared/models/control-horario.model';
import { UiCardComponent } from '../../../shared/components/ui-card/ui-card.component';
import { todayIsoDate } from '../../../shared/utils/date.util';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-control-horario',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, UiCardComponent],
  templateUrl: './control-horario.component.html'
})
export class ControlHorarioComponent implements OnInit {
  private usuarioService = inject(UsuarioService);
  private toast = inject(ToastService);
  private service = inject(ControlHorarioService);
  empleados = signal<UsuarioResponse[]>([]);
  turnos = signal<TurnoResponse[]>([]);
  banco = signal<BancoHorasResponse | null>(null);
  liquidaciones = signal<LiquidacionResponse[]>([]);
  ultimaLiquidacion = signal<LiquidacionResponse | null>(null);
  exito = signal<string | null>(null);
  empleadoId: number | null = null;
  diasSemana: { value: DiaSemana; label: string }[] = [
    { value: 'MONDAY', label: 'Lunes' },
    { value: 'TUESDAY', label: 'Martes' },
    { value: 'WEDNESDAY', label: 'Miércoles' },
    { value: 'THURSDAY', label: 'Jueves' },
    { value: 'FRIDAY', label: 'Viernes' },
    { value: 'SATURDAY', label: 'Sábado' },
    { value: 'SUNDAY', label: 'Domingo' }
  ];
  // Form turno
  nuevoDia: DiaSemana = 'MONDAY';
  nuevaHoraInicio = '08:00';
  nuevaHoraFin = '16:00';
  // Form liquidación
  desde = '';
  hasta = todayIsoDate();
  ngOnInit(): void {
    const hoy = new Date();
    this.desde = new Date(hoy.getFullYear(), hoy.getMonth(), 1).toISOString().substring(0, 10);
    this.cargarEmpleados();
  }
  private cargarEmpleados(): void {
    this.usuarioService.listarPorRol('RECEPCIONISTA').subscribe({
      next: (recep) => {
        this.usuarioService.listarPorRol('ADMIN').subscribe({
          next: (admins) => this.empleados.set([...admins, ...recep]),
          error: () => this.empleados.set(recep)
        });
      },
      error: () => this.setError('No se pudieron cargar los empleados.')
    });
  }
  onEmpleadoChange(): void {
    if (!this.empleadoId) return;
    this.refrescar();
  }
  private refrescar(): void {
    if (!this.empleadoId) return;
    const id = this.empleadoId;
    this.service.listarTurnos(id).subscribe({ next: (t) => this.turnos.set(t), error: () => {} });
    this.service.bancoHoras(id).subscribe({ next: (b) => this.banco.set(b), error: () => {} });
    this.service.historialLiquidaciones(id).subscribe({ next: (l) => this.liquidaciones.set(l), error: () => {} });
  }
  agregarTurno(): void {
    if (!this.empleadoId) { this.setError('Seleccioná un empleado.'); return; }
    const empleado = this.empleados().find(e => e.id === this.empleadoId);
    if (!empleado) return;
    this.service.crearTurno({
      empleadoEmail: empleado.email,
      diaSemana: this.nuevoDia,
      horaInicio: this.nuevaHoraInicio,
      horaFin: this.nuevaHoraFin
    }).subscribe({
      next: () => { this.setExito('Turno agregado.'); this.refrescar(); },
      error: (err) => this.setError(err.error?.message || 'Error al agregar el turno.')
    });
  }
  eliminarTurno(id: number): void {
    this.service.eliminarTurno(id).subscribe({
      next: () => { this.setExito('Turno eliminado.'); this.refrescar(); },
      error: (err) => this.setError(err.error?.message || 'Error al eliminar el turno.')
    });
  }
  liquidar(): void {
    if (!this.empleadoId) { this.setError('Seleccioná un empleado.'); return; }
    if (!this.desde || !this.hasta) { this.setError('Indicá el período.'); return; }
    this.service.liquidar({ empleadoId: this.empleadoId, desde: this.desde, hasta: this.hasta }).subscribe({
      next: (liq) => { this.ultimaLiquidacion.set(liq); this.setExito('Liquidación generada.'); this.refrescar(); },
      error: (err) => this.setError(err.error?.message || 'Error al generar la liquidación.')
    });
  }
  private setError(msg: string): void { this.toast.error(msg); this.exito.set(null); }
  private setExito(msg: string): void { this.exito.set(msg); setTimeout(() => this.exito.set(null), 4000); }
}
