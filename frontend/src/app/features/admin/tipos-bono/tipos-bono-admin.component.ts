import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { TipoBonoService } from '../../../core/services/tipo-bono.service';
import { ToastService } from '../../../core/services/toast.service';
import { TipoBonoRequest, TipoBonoResponse } from '../../../shared/models/bono.model';

/** Catálogo de bonos: el ADMIN define horas, precio y vigencia; recepción solo vende lo que hay acá. */
@Component({
  selector: 'app-tipos-bono-admin',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './tipos-bono-admin.component.html'
})
export class TiposBonoAdminComponent implements OnInit {
  private tipoBonoService = inject(TipoBonoService);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);

  tipos = signal<TipoBonoResponse[]>([]);
  cargando = signal<boolean>(true);
  error = signal<string | null>(null);
  guardando = signal<boolean>(false);
  showForm = signal<boolean>(false);
  editId = signal<number | null>(null);

  form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
    descripcion: ['', [Validators.maxLength(255)]],
    horas: [10, [Validators.required, Validators.min(1), Validators.max(500)]],
    precio: [0, [Validators.required, Validators.min(0)]],
    diasVigencia: [30, [Validators.required, Validators.min(1), Validators.max(730)]],
    activo: [true]
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.tipoBonoService.listar(true).subscribe({
      next: (data) => {
        this.tipos.set(data);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message || 'No se pudo cargar el catálogo de bonos.');
        this.cargando.set(false);
      }
    });
  }

  abrirNuevo(): void {
    this.editId.set(null);
    this.form.reset({ nombre: '', descripcion: '', horas: 10, precio: 0, diasVigencia: 30, activo: true });
    this.showForm.set(true);
  }

  editar(tipo: TipoBonoResponse): void {
    this.editId.set(tipo.id);
    this.form.reset({
      nombre: tipo.nombre,
      descripcion: tipo.descripcion ?? '',
      horas: tipo.horas,
      precio: tipo.precio,
      diasVigencia: tipo.diasVigencia,
      activo: tipo.activo
    });
    this.showForm.set(true);
  }

  cerrarForm(): void {
    this.showForm.set(false);
    this.editId.set(null);
  }

  campoInvalido(campo: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[campo];
    return control.invalid && (control.dirty || control.touched);
  }

  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const valor = this.form.getRawValue();
    const request: TipoBonoRequest = {
      nombre: valor.nombre.trim(),
      descripcion: valor.descripcion.trim() || null,
      horas: valor.horas,
      precio: valor.precio,
      diasVigencia: valor.diasVigencia,
      activo: valor.activo
    };
    const id = this.editId();
    const request$ = id ? this.tipoBonoService.actualizar(id, request) : this.tipoBonoService.crear(request);
    this.guardando.set(true);
    request$.subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.success(id ? 'Tipo de bono actualizado. Los bonos ya vendidos no cambian.' : 'Tipo de bono creado.');
        this.cerrarForm();
        this.cargar();
      },
      error: (err) => {
        this.guardando.set(false);
        this.toast.error(err.error?.message || 'No se pudo guardar el tipo de bono.');
      }
    });
  }

  desactivar(tipo: TipoBonoResponse): void {
    if (!confirm(`¿Dar de baja "${tipo.nombre}"? Deja de ofrecerse; los bonos ya vendidos siguen valiendo.`)) return;
    this.tipoBonoService.desactivar(tipo.id).subscribe({
      next: () => {
        this.toast.success('Tipo de bono dado de baja.');
        this.cargar();
      },
      error: (err) => this.toast.error(err.error?.message || 'No se pudo dar de baja el tipo de bono.')
    });
  }
}
