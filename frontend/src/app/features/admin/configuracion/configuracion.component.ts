import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ConfiguracionService } from '../../../core/services/configuracion.service';
import { ToastService } from '../../../core/services/toast.service';
import { UiCardComponent } from '../../../shared/components/ui-card/ui-card.component';

@Component({
  selector: 'app-configuracion',
  imports: [ReactiveFormsModule, RouterLink, UiCardComponent],
  templateUrl: './configuracion.component.html'
})
export class ConfiguracionComponent implements OnInit {
  private fb = inject(FormBuilder);
  private configuracionService = inject(ConfiguracionService);
  private toast = inject(ToastService);

  form: FormGroup = this.fb.group({
    comisionesHabilitadas: [false],
    porcentajeComisionRecepcion: [0, [Validators.required, Validators.min(0), Validators.max(100)]],
    diasAvisoVencimientoBono: [7, [Validators.required, Validators.min(0)]],
    horaTopeCierreSesion: ['23:59', Validators.required]
  });

  cargando = signal<boolean>(true);
  guardando = signal<boolean>(false);

  ngOnInit(): void {
    this.cargarConfiguracion();
  }

  cargarConfiguracion(): void {
    this.cargando.set(true);
    this.configuracionService.obtener().subscribe({
      next: (config) => {
        this.form.patchValue({
          comisionesHabilitadas: config.comisionesHabilitadas,
          porcentajeComisionRecepcion: config.porcentajeComisionRecepcion,
          diasAvisoVencimientoBono: config.diasAvisoVencimientoBono,
          horaTopeCierreSesion: config.horaTopeCierreSesion.substring(0, 5)
        });
        this.cargando.set(false);
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Error al cargar la configuración');
        this.cargando.set(false);
      }
    });
  }

  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.guardando.set(true);

    this.configuracionService.actualizar(this.form.value).subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.success('Configuración actualizada exitosamente.');
      },
      error: (err) => {
        this.guardando.set(false);
        this.toast.error(err.error?.message || 'Error al guardar la configuración');
      }
    });
  }
}
