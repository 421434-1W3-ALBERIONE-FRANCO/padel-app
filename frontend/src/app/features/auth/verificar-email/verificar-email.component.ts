import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';

type EstadoVerificacion = 'verificando' | 'verificado' | 'error';

/** Destino del enlace que llega por email al registrarse: /auth/verificar-email?token=... */
@Component({
  selector: 'app-verificar-email',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './verificar-email.component.html'
})
export class VerificarEmailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private toast = inject(ToastService);

  estado = signal<EstadoVerificacion>('verificando');
  mensajeError = signal<string>('');
  reenviando = signal<boolean>(false);

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]]
  });

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    // El token no queda en la barra de direcciones ni en el historial.
    this.router.navigate([], { queryParams: {}, replaceUrl: true });
    if (!token) {
      this.fallo('El enlace no es válido. Pedí uno nuevo con tu email.');
      return;
    }
    this.authService.verificarEmail(token).subscribe({
      next: () => this.estado.set('verificado'),
      error: (error: HttpErrorResponse) => this.fallo(error.error?.message || 'No pudimos confirmar tu email.')
    });
  }

  reenviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.reenviando.set(true);
    this.authService.reenviarVerificacion(this.form.getRawValue().email).subscribe({
      next: () => {
        this.reenviando.set(false);
        this.toast.success('Si la cuenta existe y falta confirmarla, te enviamos un nuevo enlace.');
      },
      error: (error: HttpErrorResponse) => {
        this.reenviando.set(false);
        this.toast.error(error.error?.message || 'No pudimos reenviar el email. Probá de nuevo en unos minutos.');
      }
    });
  }

  emailInvalido(): boolean {
    const control = this.form.controls.email;
    return control.invalid && (control.dirty || control.touched);
  }

  private fallo(mensaje: string): void {
    this.estado.set('error');
    this.mensajeError.set(mensaje);
  }
}
