import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { HttpErrorResponse } from '@angular/common/http';
import { UiCardComponent } from '../../../shared/components/ui-card/ui-card.component';
import { Scene3dComponent } from '../../../shared/components/scene-3d/scene-3d.component';
import { ApiError } from '../../../shared/models/api-error.model';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, UiCardComponent, Scene3dComponent],
  templateUrl: './login.component.html'
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private toast = inject(ToastService);

  loginForm: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    // Sin largo mínimo: las contraseñas anteriores a la política actual siguen sirviendo para entrar.
    password: ['', [Validators.required]]
  });

  isLoading = signal<boolean>(false);
  /** Email de una cuenta que todavía no confirmó su correo: se ofrece reenviar el enlace. */
  emailSinVerificar = signal<string | null>(null);
  reenviando = signal<boolean>(false);

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.emailSinVerificar.set(null);

    this.authService.login(this.loginForm.value).subscribe({
      next: () => {
        this.isLoading.set(false);
        // El admin va directo a su consola; el resto al panel de jugador.
        const destino = this.authService.userRole() === 'ADMIN' ? '/admin' : '/dashboard';
        this.router.navigate([destino]);
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading.set(false);
        const apiError = error.error as ApiError | null;
        if (apiError?.codigo === 'EMAIL_NO_VERIFICADO') {
          this.emailSinVerificar.set(this.loginForm.value.email);
          return;
        }
        // 401 credenciales incorrectas o 429 demasiados intentos (con cuánto esperar): el mensaje lo arma el backend.
        const message = apiError?.message || 'Ocurrió un error inesperado al intentar iniciar sesión.';
        this.toast.error(message);
      }
    });
  }

  reenviarVerificacion(): void {
    const email = this.emailSinVerificar();
    if (!email || this.reenviando()) return;
    this.reenviando.set(true);
    this.authService.reenviarVerificacion(email).subscribe({
      next: () => {
        this.reenviando.set(false);
        this.toast.success('Te enviamos un nuevo enlace de confirmación. Revisá tu correo (y el spam).');
      },
      error: (error: HttpErrorResponse) => {
        this.reenviando.set(false);
        this.toast.error(error.error?.message || 'No pudimos reenviar el email. Probá de nuevo en unos minutos.');
      }
    });
  }

  isFieldInvalid(field: string): boolean {
    const control = this.loginForm.get(field);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }
}
