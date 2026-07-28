import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { UsuarioService } from '../../../core/services/usuario.service';
import { ToastService } from '../../../core/services/toast.service';
import { RolUsuario, UsuarioResponse } from '../../../shared/models/usuario.model';

@Component({
  selector: 'app-staff-admin',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './staff-admin.component.html'
})
export class StaffAdminComponent implements OnInit {
  private fb = inject(FormBuilder);
  private usuarioService = inject(UsuarioService);
  private toast = inject(ToastService);

  recepcionistas = signal<UsuarioResponse[]>([]);
  admins = signal<UsuarioResponse[]>([]);
  cargando = signal<boolean>(true);
  guardando = signal<boolean>(false);
  mostrarForm = signal<boolean>(false);

  form: FormGroup = this.fb.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    apellido: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    telefono: [''],
    password: ['', [Validators.required, Validators.minLength(6)]],
    rol: ['RECEPCIONISTA' as RolUsuario, [Validators.required]]
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.usuarioService.listarPorRol('RECEPCIONISTA').subscribe({
      next: (data) => { this.recepcionistas.set(data); this.cargando.set(false); },
      error: (err) => { this.toast.error(err.error?.message || 'No se pudo cargar el staff.'); this.cargando.set(false); }
    });
    this.usuarioService.listarPorRol('ADMIN').subscribe({
      next: (data) => this.admins.set(data),
      error: () => {}
    });
  }

  toggleForm(): void {
    this.mostrarForm.update(v => !v);
  }

  crear(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toast.error('Completá los campos obligatorios.');
      return;
    }
    this.guardando.set(true);
    this.usuarioService.crearStaff(this.form.value).subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.success('Miembro de staff creado correctamente.');
        this.form.reset({ rol: 'RECEPCIONISTA' });
        this.mostrarForm.set(false);
        this.cargar();
      },
      error: (err) => {
        this.guardando.set(false);
        this.toast.error(err.error?.message || 'No se pudo crear el staff.');
      }
    });
  }

  invalido(campo: string): boolean {
    const c = this.form.get(campo);
    return !!(c && c.invalid && (c.dirty || c.touched));
  }
}
