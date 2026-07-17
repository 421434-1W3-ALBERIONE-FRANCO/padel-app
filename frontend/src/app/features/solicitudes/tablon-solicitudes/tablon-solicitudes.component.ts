import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { SolicitudPartidoService } from '../../../core/services/solicitud-partido.service';
import { CanchaAdminService } from '../../../core/services/cancha-admin.service';
import { AuthService } from '../../../core/services/auth.service';
import { CategoriaJugador } from '../../../shared/models/torneo.model';
import { CanchaResponse } from '../../../shared/models/cancha.model';
import { SolicitudPartidoResponse, TipoSolicitud } from '../../../shared/models/solicitud-partido.model';

@Component({
  selector: 'app-tablon-solicitudes',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule, FormsModule],
  templateUrl: './tablon-solicitudes.component.html'
})
export class TablonSolicitudesComponent implements OnInit {
  private solicitudService = inject(SolicitudPartidoService);
  private canchaAdminService = inject(CanchaAdminService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);

  currentUser = this.authService.currentUser;

  categorias: CategoriaJugador[] = ['PRIMERA', 'SEGUNDA', 'TERCERA', 'CUARTA', 'QUINTA', 'SEXTA', 'SEPTIMA', 'OCTAVA'];
  canchas = signal<CanchaResponse[]>([]);

  solicitudes = signal<SolicitudPartidoResponse[]>([]);
  vista = signal<'tablon' | 'mis-solicitudes' | 'mis-postulaciones'>('tablon');
  cargando = signal<boolean>(true);
  procesando = signal<boolean>(false);
  error = signal<string | null>(null);
  mensajeExito = signal<string | null>(null);

  mostrarFormulario = signal<boolean>(false);
  expandidoId = signal<number | null>(null);

  filtroForm: FormGroup = this.fb.group({
    tipoSolicitud: [''],
    categoria: ['']
  });

  solicitudForm: FormGroup = this.fb.group({
    tipoSolicitud: ['BUSCA_PAREJA' as TipoSolicitud, Validators.required],
    categoria: ['CUARTA', Validators.required],
    cantidadJugadoresFaltantes: [1, [Validators.required, Validators.min(1), Validators.max(3)]],
    fechaHoraPropuesta: ['', Validators.required],
    canchaId: [''],
    descripcion: ['', Validators.maxLength(500)]
  });

  postulacionMensaje: Record<number, string> = {};

  ngOnInit() {
    this.canchaAdminService.getCanchas().subscribe({ next: (data) => this.canchas.set(data) });
    this.cargarSolicitudes();
  }

  cambiarVista(vista: 'tablon' | 'mis-solicitudes' | 'mis-postulaciones') {
    this.vista.set(vista);
    this.cargarSolicitudes();
  }

  cargarSolicitudes() {
    this.cargando.set(true);
    this.error.set(null);

    if (this.vista() === 'mis-solicitudes') {
      this.solicitudService.misSolicitudes().subscribe(this.observadorLista());
      return;
    }
    if (this.vista() === 'mis-postulaciones') {
      this.solicitudService.misPostulaciones().subscribe(this.observadorLista());
      return;
    }

    const { tipoSolicitud, categoria } = this.filtroForm.value;
    this.solicitudService.buscar({
      tipoSolicitud: tipoSolicitud || undefined,
      categoria: categoria || undefined
    }).subscribe(this.observadorLista());
  }

  private observadorLista() {
    return {
      next: (data: SolicitudPartidoResponse[]) => {
        this.solicitudes.set(data);
        this.cargando.set(false);
      },
      error: (err: any) => {
        this.error.set(err.error?.message || 'Error al cargar las solicitudes');
        this.cargando.set(false);
      }
    };
  }

  aplicarFiltros() {
    this.cargarSolicitudes();
  }

  toggleFormulario() {
    this.mostrarFormulario.set(!this.mostrarFormulario());
  }

  toggleExpandido(id: number) {
    this.expandidoId.set(this.expandidoId() === id ? null : id);
  }

  private mostrarError(msg: string) {
    this.error.set(msg);
    setTimeout(() => this.error.set(null), 5000);
  }

  private mostrarExito(msg: string) {
    this.mensajeExito.set(msg);
    setTimeout(() => this.mensajeExito.set(null), 4000);
  }

  crearSolicitud() {
    if (this.solicitudForm.invalid) {
      this.solicitudForm.markAllAsTouched();
      this.mostrarError('Completá los campos obligatorios marcados en rojo antes de publicar.');
      return;
    }
    this.procesando.set(true);
    const value = this.solicitudForm.value;
    this.solicitudService.crear({
      ...value,
      canchaId: value.canchaId ? Number(value.canchaId) : null
    }).subscribe({
      next: () => {
        this.procesando.set(false);
        this.mostrarFormulario.set(false);
        this.solicitudForm.reset({ tipoSolicitud: 'BUSCA_PAREJA', categoria: 'CUARTA', cantidadJugadoresFaltantes: 1, canchaId: '' });
        this.mostrarExito('Solicitud publicada exitosamente');
        this.cargarSolicitudes();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al crear la solicitud');
        this.procesando.set(false);
      }
    });
  }

  cancelarSolicitud(id: number) {
    if (!confirm('¿Seguro que querés cancelar esta solicitud?')) return;
    this.procesando.set(true);
    this.solicitudService.cancelar(id).subscribe({
      next: () => {
        this.procesando.set(false);
        this.mostrarExito('Solicitud cancelada');
        this.cargarSolicitudes();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al cancelar la solicitud');
        this.procesando.set(false);
      }
    });
  }

  postularse(id: number) {
    this.procesando.set(true);
    this.solicitudService.postularse(id, { mensaje: this.postulacionMensaje[id] || null }).subscribe({
      next: () => {
        this.procesando.set(false);
        delete this.postulacionMensaje[id];
        this.mostrarExito('Te postulaste exitosamente');
        this.cargarSolicitudes();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al postularte');
        this.procesando.set(false);
      }
    });
  }

  aceptarPostulacion(solicitudId: number, postulacionId: number) {
    this.procesando.set(true);
    this.solicitudService.aceptarPostulacion(solicitudId, postulacionId).subscribe({
      next: () => {
        this.procesando.set(false);
        this.mostrarExito('Postulación aceptada');
        this.cargarSolicitudes();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al aceptar la postulación');
        this.procesando.set(false);
      }
    });
  }

  rechazarPostulacion(solicitudId: number, postulacionId: number) {
    this.procesando.set(true);
    this.solicitudService.rechazarPostulacion(solicitudId, postulacionId).subscribe({
      next: () => {
        this.procesando.set(false);
        this.mostrarExito('Postulación rechazada');
        this.cargarSolicitudes();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al rechazar la postulación');
        this.procesando.set(false);
      }
    });
  }

  esCreador(solicitud: SolicitudPartidoResponse): boolean {
    return solicitud.creadorEmail === this.currentUser()?.email;
  }

  yaMePostule(solicitud: SolicitudPartidoResponse): boolean {
    const email = this.currentUser()?.email;
    return solicitud.postulaciones.some(p => p.jugadorEmail === email);
  }

  clasePostulacion(estado: string): string {
    switch (estado) {
      case 'ACEPTADA':
        return 'bg-emerald-500/10 text-emerald-400';
      case 'RECHAZADA':
        return 'bg-red-500/10 text-red-400';
      case 'PENDIENTE':
        return 'bg-amber-500/10 text-amber-400';
      default:
        return 'bg-slate-800 text-slate-400';
    }
  }

  invalido(campo: string): boolean {
    const control = this.solicitudForm.get(campo);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  claseEstadoSolicitud(estado: string): string {
    switch (estado) {
      case 'ABIERTA':
        return 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20';
      case 'COMPLETA':
        return 'bg-teal-500/10 text-teal-400 border border-teal-500/20';
      case 'CANCELADA':
        return 'bg-red-500/10 text-red-400 border border-red-500/20';
      default:
        return 'bg-slate-800 text-slate-400';
    }
  }
}
