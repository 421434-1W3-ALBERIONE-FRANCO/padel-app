import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { CanchaAdminService } from '../../../core/services/cancha-admin.service';
import { CanchaResponse, TipoSuelo } from '../../../shared/models/cancha.model';
import { FranjaHorariaResponse } from '../../../shared/models/franja.model';
import { BloqueoCanchaResponse } from '../../../shared/models/bloqueo.model';

@Component({
  selector: 'app-canchas-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './canchas-admin.component.html',
  styleUrls: ['./canchas-admin.component.css']
})
export class CanchasAdminComponent implements OnInit {
  private canchaAdminService = inject(CanchaAdminService);
  private fb = inject(FormBuilder);

  // Signals para estado de datos
  canchas = signal<CanchaResponse[]>([]);
  canchaSeleccionada = signal<CanchaResponse | null>(null);
  franjas = signal<FranjaHorariaResponse[]>([]);
  bloqueos = signal<BloqueoCanchaResponse[]>([]);

  // Signals para estado de UI
  cargando = signal<boolean>(false);
  cargandoDetalles = signal<boolean>(false);
  mensajeError = signal<string | null>(null);
  mensajeExito = signal<string | null>(null);
  activeTab = signal<'franjas' | 'bloqueos'>('franjas');

  // Formularios reactivos
  canchaForm!: FormGroup;
  franjaForm!: FormGroup;
  bloqueoForm!: FormGroup;

  // Modales / Edición
  editCanchaId = signal<number | null>(null);
  editFranjaId = signal<number | null>(null);
  editBloqueoId = signal<number | null>(null);

  showCanchaForm = signal<boolean>(false);
  showFranjaForm = signal<boolean>(false);
  showBloqueoForm = signal<boolean>(false);

  diasSemana = [
    { value: 'MONDAY', label: 'Lunes' },
    { value: 'TUESDAY', label: 'Martes' },
    { value: 'WEDNESDAY', label: 'Miércoles' },
    { value: 'THURSDAY', label: 'Jueves' },
    { value: 'FRIDAY', label: 'Viernes' },
    { value: 'SATURDAY', label: 'Sábado' },
    { value: 'SUNDAY', label: 'Domingo' }
  ];

  ngOnInit() {
    this.initForms();
    this.cargarCanchas();
  }

  private initForms() {
    this.canchaForm = this.fb.group({
      nombre: ['', [Validators.required, Validators.maxLength(100)]],
      tipoSuelo: ['BLINDEX', Validators.required],
      techada: [false],
      tieneLuz: [true]
    });

    this.franjaForm = this.fb.group({
      horaInicio: ['', Validators.required],
      horaFin: ['', Validators.required],
      precioBase: [0, [Validators.required, Validators.min(0)]],
      precioNocturno: [0, [Validators.required, Validators.min(0)]],
      diasAplicables: this.fb.array([], Validators.required)
    });

    this.bloqueoForm = this.fb.group({
      fechaDesde: ['', Validators.required],
      fechaHasta: ['', Validators.required],
      horaDesde: ['00:00', Validators.required],
      horaHasta: ['23:59', Validators.required],
      motivo: ['', [Validators.required, Validators.maxLength(255)]]
    });
  }

  // Cargar datos
  cargarCanchas() {
    this.cargando.set(true);
    this.mensajeError.set(null);
    this.canchaAdminService.getCanchas().subscribe({
      next: (data) => {
        this.canchas.set(data);
        this.cargando.set(false);
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al cargar las canchas');
        this.cargando.set(false);
      }
    });
  }

  seleccionarCancha(cancha: CanchaResponse) {
    this.canchaSeleccionada.set(cancha);
    this.cargarDetallesCancha(cancha.id);
  }

  cargarDetallesCancha(canchaId: number) {
    this.cargandoDetalles.set(true);
    this.franjas.set([]);
    this.bloqueos.set([]);

    // Cargar Franjas
    this.canchaAdminService.getFranjas(canchaId).subscribe({
      next: (data) => {
        this.franjas.set(data);
        this.checkDetallesCargados();
      },
      error: (err) => {
        this.mostrarError('Error al cargar las franjas horarias');
        this.cargandoDetalles.set(false);
      }
    });

    // Cargar Bloqueos
    this.canchaAdminService.getBloqueos(canchaId).subscribe({
      next: (data) => {
        this.bloqueos.set(data);
        this.checkDetallesCargados();
      },
      error: (err) => {
        this.mostrarError('Error al cargar los bloqueos temporales');
        this.cargandoDetalles.set(false);
      }
    });
  }

  private checkDetallesCargados() {
    this.cargandoDetalles.set(false);
  }

  // CRUD Canchas
  guardarCancha() {
    if (this.canchaForm.invalid) {
      return;
    }

    const payload = this.canchaForm.value;
    const request$ = this.editCanchaId() 
      ? this.canchaAdminService.actualizarCancha(this.editCanchaId()!, payload)
      : this.canchaAdminService.crearCancha(payload);

    this.cargando.set(true);
    request$.subscribe({
      next: (res) => {
        this.mostrarExito(this.editCanchaId() ? 'Cancha actualizada' : 'Cancha creada');
        this.cancelarEdicionCancha();
        this.cargarCanchas();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al guardar la cancha');
        this.cargando.set(false);
      }
    });
  }

  editarCancha(cancha: CanchaResponse) {
    this.editCanchaId.set(cancha.id);
    this.showCanchaForm.set(true);
    this.canchaForm.patchValue({
      nombre: cancha.nombre,
      tipoSuelo: cancha.tipoSuelo,
      techada: cancha.techada,
      tieneLuz: cancha.tieneLuz
    });
  }

  desactivarCancha(id: number) {
    if (!confirm('¿Seguro que querés desactivar esta cancha?')) return;

    this.cargando.set(true);
    this.canchaAdminService.desactivarCancha(id).subscribe({
      next: () => {
        this.mostrarExito('Cancha desactivada exitosamente');
        if (this.canchaSeleccionada()?.id === id) {
          this.canchaSeleccionada.set(null);
        }
        this.cargarCanchas();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al desactivar la cancha');
        this.cargando.set(false);
      }
    });
  }

  cancelarEdicionCancha() {
    this.showCanchaForm.set(false);
    this.editCanchaId.set(null);
    this.canchaForm.reset({ tipoSuelo: 'BLINDEX', techada: false, tieneLuz: true });
  }

  // CRUD Franjas
  guardarFranja() {
    if (this.franjaForm.invalid || !this.canchaSeleccionada()) {
      return;
    }

    const value = this.franjaForm.value;
    const payload = {
      ...value,
      diasAplicables: this.getDiasSeleccionados()
    };

    const canchaId = this.canchaSeleccionada()!.id;
    const request$ = this.editFranjaId()
      ? this.canchaAdminService.actualizarFranja(this.editFranjaId()!, payload)
      : this.canchaAdminService.crearFranja(canchaId, payload);

    this.cargandoDetalles.set(true);
    request$.subscribe({
      next: () => {
        this.mostrarExito(this.editFranjaId() ? 'Franja horaria actualizada' : 'Franja horaria agregada');
        this.cancelarEdicionFranja();
        this.cargarDetallesCancha(canchaId);
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al guardar la franja horaria');
        this.cargandoDetalles.set(false);
      }
    });
  }

  editarFranja(franja: FranjaHorariaResponse) {
    this.editFranjaId.set(franja.id);
    this.showFranjaForm.set(true);
    this.franjaForm.patchValue({
      horaInicio: franja.horaInicio.substring(0, 5),
      horaFin: franja.horaFin.substring(0, 5),
      precioBase: franja.precioBase,
      precioNocturno: franja.precioNocturno
    });
    // Marcar checkboxes
    this.setDiasSeleccionados(franja.diasAplicables);
  }

  eliminarFranja(id: number) {
    if (!confirm('¿Seguro que deseas eliminar esta franja horaria permanentemente?')) return;

    this.cargandoDetalles.set(true);
    this.canchaAdminService.eliminarFranja(id).subscribe({
      next: () => {
        this.mostrarExito('Franja horaria eliminada');
        this.cargarDetallesCancha(this.canchaSeleccionada()!.id);
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al eliminar la franja horaria');
        this.cargandoDetalles.set(false);
      }
    });
  }

  cancelarEdicionFranja() {
    this.showFranjaForm.set(false);
    this.editFranjaId.set(null);
    this.franjaForm.reset({ precioBase: 0, precioNocturno: 0 });
    this.clearDiasSeleccionados();
  }

  // CRUD Bloqueos
  guardarBloqueo() {
    if (this.bloqueoForm.invalid || !this.canchaSeleccionada()) {
      return;
    }

    const payload = this.bloqueoForm.value;
    const canchaId = this.canchaSeleccionada()!.id;
    const request$ = this.editBloqueoId()
      ? this.canchaAdminService.actualizarBloqueo(this.editBloqueoId()!, payload)
      : this.canchaAdminService.crearBloqueo(canchaId, payload);

    this.cargandoDetalles.set(true);
    request$.subscribe({
      next: () => {
        this.mostrarExito(this.editBloqueoId() ? 'Bloqueo temporal actualizado' : 'Bloqueo temporal registrado');
        this.cancelarEdicionBloqueo();
        this.cargarDetallesCancha(canchaId);
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al guardar el bloqueo');
        this.cargandoDetalles.set(false);
      }
    });
  }

  editarBloqueo(bloqueo: BloqueoCanchaResponse) {
    this.editBloqueoId.set(bloqueo.id);
    this.showBloqueoForm.set(true);
    this.bloqueoForm.patchValue({
      fechaDesde: bloqueo.fechaDesde,
      fechaHasta: bloqueo.fechaHasta,
      horaDesde: bloqueo.horaDesde.substring(0, 5),
      horaHasta: bloqueo.horaHasta.substring(0, 5),
      motivo: bloqueo.motivo
    });
  }

  eliminarBloqueo(id: number) {
    if (!confirm('¿Seguro que deseas eliminar este bloqueo?')) return;

    this.cargandoDetalles.set(true);
    this.canchaAdminService.eliminarBloqueo(id).subscribe({
      next: () => {
        this.mostrarExito('Bloqueo eliminado exitosamente');
        this.cargarDetallesCancha(this.canchaSeleccionada()!.id);
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al eliminar el bloqueo');
        this.cargandoDetalles.set(false);
      }
    });
  }

  cancelarEdicionBloqueo() {
    this.showBloqueoForm.set(false);
    this.editBloqueoId.set(null);
    this.bloqueoForm.reset({ horaDesde: '00:00', horaHasta: '23:59' });
  }

  // Helpers para manejo de checkboxes de Días Aplicables
  onDiaChange(event: any) {
    const value = event.target.value;
    const checked = event.target.checked;
    const diasArray = this.franjaForm.get('diasAplicables')?.value as string[];

    if (checked) {
      this.franjaForm.get('diasAplicables')?.setValue([...diasArray, value]);
    } else {
      this.franjaForm.get('diasAplicables')?.setValue(diasArray.filter(x => x !== value));
    }
  }

  isDiaChecked(dia: string): boolean {
    const diasArray = this.franjaForm.get('diasAplicables')?.value as string[];
    return diasArray ? diasArray.includes(dia) : false;
  }

  private getDiasSeleccionados(): string[] {
    return this.franjaForm.get('diasAplicables')?.value || [];
  }

  private setDiasSeleccionados(dias: string[]) {
    this.franjaForm.get('diasAplicables')?.setValue(dias);
  }

  private clearDiasSeleccionados() {
    this.franjaForm.get('diasAplicables')?.setValue([]);
  }

  // Helpers de alertas
  private mostrarError(msg: string) {
    this.mensajeError.set(msg);
    setTimeout(() => this.mensajeError.set(null), 5000);
  }

  private mostrarExito(msg: string) {
    this.mensajeExito.set(msg);
    setTimeout(() => this.mensajeExito.set(null), 4000);
  }

  setTab(tab: 'franjas' | 'bloqueos') {
    this.activeTab.set(tab);
  }
}
