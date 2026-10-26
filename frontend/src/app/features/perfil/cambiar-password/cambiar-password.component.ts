import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { UsuarioService } from '../../../core/services/usuario.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { HttpErrorResponse } from '@angular/common/http';
import { UiCardComponent } from '../../../shared/components/ui-card/ui-card.component';

@Component({
  selector: 'app-cambiar-password',
  imports: [ReactiveFormsModule, RouterLink, UiCardComponent],
  templateUrl: './cambiar-password.component.html'
})
export class CambiarPasswordComponent {
  private fb = inject(FormBuilder);
  private usuarioService = inject(UsuarioService);
  private authService = inject(AuthService);
  private toast = inject(ToastService);

  form: FormGroup = this.fb.group({
    passwordActual: ['', [Validators.required]],
    passwordNueva: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(72)]]
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
        // El backend invalida todas las sesiones (incluida esta): hay que volver a entrar con la nueva.
        this.toast.success('Contraseña actualizada. Por seguridad cerramos tus sesiones: ingresá con la nueva contraseña.');
        this.authService.logout(false);
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
