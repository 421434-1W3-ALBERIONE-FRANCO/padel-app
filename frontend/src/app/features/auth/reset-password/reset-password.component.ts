import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password.component.html'
})
export class ResetPasswordComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private toast = inject(ToastService);

  private token = this.route.snapshot.queryParamMap.get('token') ?? '';

  form: FormGroup = this.fb.group({
    passwordNueva: ['', [Validators.required, Validators.minLength(6)]]
  });

  tokenPresente = signal<boolean>(!!this.token);
  isLoading = signal<boolean>(false);

  onSubmit(): void {
    if (this.form.invalid || !this.token) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);

    this.authService.resetearPassword({ token: this.token, passwordNueva: this.form.value.passwordNueva }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.toast.success('Tu contraseña fue actualizada. Redirigiendo al login...');
        setTimeout(() => this.router.navigate(['/auth/login']), 2000);
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading.set(false);
        const message = error.error?.message || 'El enlace es inválido o expiró.';
        this.toast.error(message);
      }
    });
  }

  isFieldInvalid(field: string): boolean {
    const control = this.form.get(field);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }
}
