import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TorneoService } from '../../../core/services/torneo.service';
import { AuthService } from '../../../core/services/auth.service';
import { CategoriaJugador, EstadoTorneo, TipoTorneo, TorneoResponse } from '../../../shared/models/torneo.model';

@Component({
  selector: 'app-lista-torneos',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  templateUrl: './lista-torneos.component.html'
})
export class ListaTorneosComponent implements OnInit {
  private torneoService = inject(TorneoService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);

  currentUser = this.authService.currentUser;

  torneos = signal<TorneoResponse[]>([]);
  cargando = signal<boolean>(true);
  error = signal<string | null>(null);
  mensajeExito = signal<string | null>(null);

  mostrarFormulario = signal<boolean>(false);
  guardando = signal<boolean>(false);

  categorias: CategoriaJugador[] = ['PRIMERA', 'SEGUNDA', 'TERCERA', 'CUARTA', 'QUINTA', 'SEXTA', 'SEPTIMA', 'OCTAVA'];
  tipos: TipoTorneo[] = ['LIGA', 'TORNEO', 'TORNEO_EXPRESS'];
  estados: EstadoTorneo[] = ['INSCRIPCION_ABIERTA', 'EN_CURSO', 'FINALIZADO', 'CANCELADO'];

  filtroForm: FormGroup = this.fb.group({
    categoria: [''],
    tipo: [''],
    estado: ['']
  });

  torneoForm: FormGroup = this.fb.group({
    nombre: ['', [Validators.required, Validators.maxLength(150)]],
    tipo: ['TORNEO', Validators.required],
    formato: ['ELIMINACION_DIRECTA', Validators.required],
    categoria: ['CUARTA', Validators.required],
    fechaInicio: ['', Validators.required],
    fechaFin: ['', Validators.required],
    maxParejas: [8, [Validators.required, Validators.min(2)]],
    precioInscripcion: [0, [Validators.required, Validators.min(0)]],
    descripcion: ['', Validators.maxLength(500)]
  });

  ngOnInit() {
    this.cargarTorneos();
  }

  cargarTorneos() {
    this.cargando.set(true);
    this.error.set(null);
    const { categoria, tipo, estado } = this.filtroForm.value;
    this.torneoService.buscar({
      categoria: categoria || undefined,
      tipo: tipo || undefined,
      estado: estado || undefined
    }).subscribe({
      next: (data) => {
        this.torneos.set(data);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Error al cargar los torneos');
        this.cargando.set(false);
      }
    });
  }

  aplicarFiltros() {
    this.cargarTorneos();
  }

  limpiarFiltros() {
    this.filtroForm.reset({ categoria: '', tipo: '', estado: '' });
    this.cargarTorneos();
  }

  toggleFormulario() {
    this.mostrarFormulario.set(!this.mostrarFormulario());
  }

  crearTorneo() {
    if (this.torneoForm.invalid) {
      this.torneoForm.markAllAsTouched();
      this.error.set('Completá los campos obligatorios marcados en rojo antes de guardar.');
      return;
    }
    this.guardando.set(true);
    this.torneoService.crear(this.torneoForm.value).subscribe({
      next: () => {
        this.guardando.set(false);
        this.mostrarFormulario.set(false);
        this.torneoForm.reset({ tipo: 'TORNEO', formato: 'ELIMINACION_DIRECTA', categoria: 'CUARTA', maxParejas: 8, precioInscripcion: 0 });
        this.mensajeExito.set('Torneo creado exitosamente');
        setTimeout(() => this.mensajeExito.set(null), 4000);
        this.cargarTorneos();
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Error al crear el torneo');
        this.guardando.set(false);
      }
    });
  }

  invalido(campo: string): boolean {
    const control = this.torneoForm.get(campo);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  claseEstado(estado: EstadoTorneo): string {
    switch (estado) {
      case 'INSCRIPCION_ABIERTA':
        return 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20';
      case 'EN_CURSO':
        return 'bg-amber-500/10 text-amber-400 border border-amber-500/20';
      case 'FINALIZADO':
        return 'bg-slate-800 text-slate-400 border border-slate-700';
      case 'CANCELADO':
        return 'bg-red-500/10 text-red-400 border border-red-500/20';
      default:
        return 'bg-slate-800 text-slate-400';
    }
  }
}
