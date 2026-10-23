import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TorneoService } from '../../../core/services/torneo.service';
import { AuthService } from '../../../core/services/auth.service';
import { CategoriaJugador, EstadoTorneo, TipoTorneo, TorneoResponse } from '../../../shared/models/torneo.model';
import { maxIsoDate, rangoDeFechaValidator, todayIsoDate } from '../../../shared/utils/date.util';
import { ToastService } from '../../../core/services/toast.service';
import {
  claseEstadoTorneo,
  colorCategoria,
  cuentaRegresiva,
  dosDigitos,
  etiquetaEstado,
  etiquetaFormato,
  etiquetaTipo,
  fechaCorta,
  fondoPoster,
  nombreCategoria,
  numeroCategoria
} from '../utils/torneo-visual.util';

/** Orden de los pósters: lo que se está jugando y lo que abre inscripción, antes que lo terminado. */
const PRIORIDAD_ESTADO: Record<EstadoTorneo, number> = {
  EN_CURSO: 0,
  INSCRIPCION_ABIERTA: 1,
  SUSPENDIDO: 2,
  FINALIZADO: 3,
  CANCELADO: 4
};

@Component({
  selector: 'app-lista-torneos',
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  templateUrl: './lista-torneos.component.html'
})
export class ListaTorneosComponent implements OnInit {
  private torneoService = inject(TorneoService);
  private toast = inject(ToastService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);

  readonly claseEstadoTorneo = claseEstadoTorneo;
  readonly colorCategoria = colorCategoria;
  readonly dosDigitos = dosDigitos;
  readonly etiquetaEstado = etiquetaEstado;
  readonly etiquetaFormato = etiquetaFormato;
  readonly etiquetaTipo = etiquetaTipo;
  readonly fechaCorta = fechaCorta;
  readonly fondoPoster = fondoPoster;
  readonly nombreCategoria = nombreCategoria;
  readonly numeroCategoria = numeroCategoria;

  currentUser = this.authService.currentUser;
  torneos = signal<TorneoResponse[]>([]);
  cargando = signal<boolean>(true);
  mostrarFormulario = signal<boolean>(false);
  guardando = signal<boolean>(false);
  // Reloj de las cuentas regresivas (resolución de minutos).
  ahora = signal(new Date());

  torneosOrdenados = computed(() => [...this.torneos()].sort((a, b) =>
    PRIORIDAD_ESTADO[a.estado] - PRIORIDAD_ESTADO[b.estado] || a.fechaInicio.localeCompare(b.fechaInicio)));

  categorias: CategoriaJugador[] = ['PRIMERA', 'SEGUNDA', 'TERCERA', 'CUARTA', 'QUINTA', 'SEXTA', 'SEPTIMA', 'OCTAVA'];
  tipos: TipoTorneo[] = ['LIGA', 'TORNEO', 'TORNEO_EXPRESS'];
  estados: EstadoTorneo[] = ['INSCRIPCION_ABIERTA', 'EN_CURSO', 'SUSPENDIDO', 'FINALIZADO', 'CANCELADO'];
  // La fecha de inicio nunca puede ser anterior a hoy (el día en que se crea el
  // torneo) y el año no puede irse de un rango razonable (evita typos como
  // escribir "20260" en vez de "2026" en el input de fecha).
  fechaMin = todayIsoDate();
  fechaMax = maxIsoDate();
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
    fechaInicio: [todayIsoDate(), [Validators.required, rangoDeFechaValidator(this.fechaMin, this.fechaMax)]],
    fechaFin: ['', [Validators.required, rangoDeFechaValidator(this.fechaMin, this.fechaMax)]],
    maxParejas: [8, [Validators.required, Validators.min(2)]],
    precioInscripcion: [0, [Validators.required, Validators.min(0)]],
    descripcion: ['', Validators.maxLength(500)]
  });

  constructor() {
    const reloj = setInterval(() => this.ahora.set(new Date()), 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(reloj));
  }

  ngOnInit() {
    this.cargarTorneos();
  }
  cargarTorneos() {
    this.cargando.set(true);
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
        this.toast.error(err.error?.message || 'Error al cargar los torneos');
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
      this.toast.error('Completá los campos obligatorios marcados en rojo antes de guardar.');
      return;
    }
    this.guardando.set(true);
    this.torneoService.crear(this.torneoForm.value).subscribe({
      next: () => {
        this.guardando.set(false);
        this.mostrarFormulario.set(false);
        this.torneoForm.reset({ tipo: 'TORNEO', formato: 'ELIMINACION_DIRECTA', categoria: 'CUARTA', fechaInicio: todayIsoDate(), maxParejas: 8, precioInscripcion: 0 });
        this.toast.success('Torneo creado exitosamente');
        this.cargarTorneos();
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Error al crear el torneo');
        this.guardando.set(false);
      }
    });
  }
  invalido(campo: string): boolean {
    const control = this.torneoForm.get(campo);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  // --- Póster ---

  cuenta(torneo: TorneoResponse) {
    return torneo.estado === 'INSCRIPCION_ABIERTA' ? cuentaRegresiva(torneo.fechaInicio, this.ahora()) : null;
  }

  cupo(torneo: TorneoResponse): number {
    return torneo.maxParejas > 0 ? Math.min(100, Math.round(torneo.parejasInscriptas / torneo.maxParejas * 100)) : 0;
  }

  lugaresLibres(torneo: TorneoResponse): string {
    const libres = Math.max(0, torneo.maxParejas - torneo.parejasInscriptas);
    if (libres === 0) return 'Cupo completo';
    return libres === 1 ? 'Queda 1 lugar' : `Quedan ${libres} lugares`;
  }

  /** "$ 12.000" con el separador de miles argentino (Intl del navegador: la app no registra es-AR en Angular). */
  precio(valor: number): string {
    return valor > 0 ? `$ ${valor.toLocaleString('es-AR', { maximumFractionDigits: 0 })}` : 'Gratis';
  }

  accion(torneo: TorneoResponse): string {
    if (torneo.estado === 'INSCRIPCION_ABIERTA') return torneo.parejasInscriptas < torneo.maxParejas ? 'Inscribirse' : 'Ver parejas';
    return torneo.formato === 'LIGA_TODOS_CONTRA_TODOS' ? 'Ver tabla' : 'Ver cuadro';
  }
}
