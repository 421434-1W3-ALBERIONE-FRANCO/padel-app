import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { UsuarioService } from '../../../core/services/usuario.service';
import { ToastService } from '../../../core/services/toast.service';
import { HttpErrorResponse } from '@angular/common/http';
import { UiCardComponent } from '../../../shared/components/ui-card/ui-card.component';

@Component({
  selector: 'app-cambiar-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, UiCardComponent],
  templateUrl: './cambiar-password.component.html'
})
export class CambiarPasswordComponent {
  private fb = inject(FormBuilder);
  private usuarioService = inject(UsuarioService);
  private toast = inject(ToastService);

  form: FormGroup = this.fb.group({
    passwordActual: ['', [Validators.required]],
    passwordNueva: ['', [Validators.required, Validators.minLength(6)]]
  });

  isLoading = signal<boolean>(false);

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);

    this.usuarioService.cambiarPassword(this.form.value).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.toast.success('Tu contraseña fue actualizada exitosamente.');
        this.form.reset();
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading.set(false);
        const message = error.error?.message || 'Ocurrió un error al cambiar la contraseña.';
        this.toast.error(message);
      }
    });
  }

  isFieldInvalid(field: string): boolean {
    const control = this.form.get(field);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }
}
