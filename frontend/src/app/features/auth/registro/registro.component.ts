import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { HttpErrorResponse } from '@angular/common/http';
import { UsuarioResponse } from '../../../shared/models/usuario.model';

@Component({
  selector: 'app-registro',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './registro.component.html'
})
export class RegistroComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private toast = inject(ToastService);

  registroForm: FormGroup = this.fb.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    apellido: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    telefono: ['', [Validators.maxLength(50)]],
    // Misma política que el backend: largo antes que combinaciones de caracteres (las comunes las rechaza él).
    password: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(72)]]
  });

  isLoading = signal<boolean>(false);
  /** Cuenta creada que espera la confirmación del email. */
  emailPendiente = signal<string | null>(null);
  reenviando = signal<boolean>(false);

  onSubmit(): void {
    if (this.registroForm.invalid) {
      this.registroForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);

    const valor = this.registroForm.value;
    this.authService.registrar({ ...valor, telefono: valor.telefono?.trim() || undefined }).subscribe({
      next: (usuario: UsuarioResponse) => {
        this.isLoading.set(false);
        if (!usuario.emailVerificado) {
          this.emailPendiente.set(usuario.email);
          return;
        }
        this.toast.success('¡Registro exitoso! Redirigiendo al login...');

        // Redirigir después de 2 segundos para dar feedback visual
        setTimeout(() => {
          this.router.navigate(['/auth/login']);
        }, 2000);
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading.set(false);
        // Extraer mensaje del formato ApiError del backend
        const message = error.error?.message || 'Ocurrió un error al registrar el usuario.';
        this.toast.error(message);
      }
    });
  }

  reenviarVerificacion(): void {
    const email = this.emailPendiente();
    if (!email || this.reenviando()) return;
    this.reenviando.set(true);
    this.authService.reenviarVerificacion(email).subscribe({
      next: () => {
        this.reenviando.set(false);
        this.toast.success('Te enviamos un nuevo enlace de confirmación.');
      },
      error: (error: HttpErrorResponse) => {
        this.reenviando.set(false);
        this.toast.error(error.error?.message || 'No pudimos reenviar el email. Probá de nuevo en unos minutos.');
      }
    });
  }

  isFieldInvalid(field: string): boolean {
    const control = this.registroForm.get(field);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }
}
