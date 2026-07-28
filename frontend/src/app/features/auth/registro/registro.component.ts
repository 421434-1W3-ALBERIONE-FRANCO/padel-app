import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { HttpErrorResponse } from '@angular/common/http';
import { RolUsuario } from '../../../shared/models/usuario.model';

@Component({
  selector: 'app-registro',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
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
    password: ['', [
      Validators.required,
      Validators.minLength(8),
      // Al menos una mayúscula, una minúscula y un número.
      Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/)
    ]],
    rol: ['JUGADOR' as RolUsuario, [Validators.required]] // JUGADOR por defecto
  });

  isLoading = signal<boolean>(false);

  onSubmit(): void {
    if (this.registroForm.invalid) {
      this.registroForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);

    this.authService.registrar(this.registroForm.value).subscribe({
      next: () => {
        this.isLoading.set(false);
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

  isFieldInvalid(field: string): boolean {
    const control = this.registroForm.get(field);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }
}
