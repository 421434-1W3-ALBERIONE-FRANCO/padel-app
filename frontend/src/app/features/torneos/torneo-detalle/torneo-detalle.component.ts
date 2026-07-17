import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TorneoService } from '../../../core/services/torneo.service';
import { AuthService } from '../../../core/services/auth.service';
import { InscripcionTorneoResponse, PartidoTorneoResponse, TorneoResponse } from '../../../shared/models/torneo.model';

@Component({
  selector: 'app-torneo-detalle',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  templateUrl: './torneo-detalle.component.html'
})
export class TorneoDetalleComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private torneoService = inject(TorneoService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);

  currentUser = this.authService.currentUser;
  torneoId = Number(this.route.snapshot.paramMap.get('id'));

  torneo = signal<TorneoResponse | null>(null);
  inscripciones = signal<InscripcionTorneoResponse[]>([]);
  partidos = signal<PartidoTorneoResponse[]>([]);

  activeTab = signal<'inscripciones' | 'fixture'>('inscripciones');
  cargando = signal<boolean>(true);
  procesando = signal<boolean>(false);
  error = signal<string | null>(null);
  mensajeExito = signal<string | null>(null);

  editandoResultadoId = signal<number | null>(null);

  inscripcionForm: FormGroup = this.fb.group({
    companeroEmail: ['', [Validators.required, Validators.email]]
  });

  resultadoForm: FormGroup = this.fb.group({
    setsPareja1: [0, [Validators.required, Validators.min(0)]],
    setsPareja2: [0, [Validators.required, Validators.min(0)]]
  });

  ngOnInit() {
    this.cargarTodo();
  }

  cargarTodo() {
    this.cargando.set(true);
    this.error.set(null);

    this.torneoService.obtenerPorId(this.torneoId).subscribe({
      next: (data) => {
        this.torneo.set(data);
        this.cargarInscripciones();
        this.cargarPartidos();
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Error al cargar el torneo');
        this.cargando.set(false);
      }
    });
  }

  cargarInscripciones() {
    this.torneoService.listarInscripciones(this.torneoId).subscribe({
      next: (data) => {
        this.inscripciones.set(data);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false)
    });
  }

  cargarPartidos() {
    this.torneoService.listarPartidos(this.torneoId).subscribe({
      next: (data) => this.partidos.set(data)
    });
  }

  setTab(tab: 'inscripciones' | 'fixture') {
    this.activeTab.set(tab);
  }

  private mostrarError(msg: string) {
    this.error.set(msg);
    setTimeout(() => this.error.set(null), 5000);
  }

  private mostrarExito(msg: string) {
    this.mensajeExito.set(msg);
    setTimeout(() => this.mensajeExito.set(null), 4000);
  }

  inscribirPareja() {
    if (this.inscripcionForm.invalid) {
      this.inscripcionForm.markAllAsTouched();
      this.mostrarError('Ingresá un email válido de tu compañero/a.');
      return;
    }
    this.procesando.set(true);
    this.torneoService.inscribirPareja(this.torneoId, this.inscripcionForm.value).subscribe({
      next: () => {
        this.procesando.set(false);
        this.inscripcionForm.reset();
        this.mostrarExito('Pareja inscripta exitosamente');
        this.cargarTodo();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al inscribir la pareja');
        this.procesando.set(false);
      }
    });
  }

  cancelarInscripcion(inscripcionId: number) {
    if (!confirm('¿Seguro que querés cancelar esta inscripción?')) return;
    this.procesando.set(true);
    this.torneoService.cancelarInscripcion(this.torneoId, inscripcionId).subscribe({
      next: () => {
        this.procesando.set(false);
        this.mostrarExito('Inscripción cancelada');
        this.cargarInscripciones();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al cancelar la inscripción');
        this.procesando.set(false);
      }
    });
  }

  cancelarTorneo() {
    if (!confirm('¿Seguro que querés cancelar este torneo?')) return;
    this.procesando.set(true);
    this.torneoService.cancelar(this.torneoId).subscribe({
      next: () => {
        this.procesando.set(false);
        this.mostrarExito('Torneo cancelado');
        this.cargarTodo();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al cancelar el torneo');
        this.procesando.set(false);
      }
    });
  }

  generarFixture() {
    this.procesando.set(true);
    this.torneoService.generarFixture(this.torneoId).subscribe({
      next: () => {
        this.procesando.set(false);
        this.mostrarExito('Fixture generado exitosamente');
        this.activeTab.set('fixture');
        this.cargarTodo();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al generar el fixture');
        this.procesando.set(false);
      }
    });
  }

  generarSiguienteRonda() {
    this.procesando.set(true);
    this.torneoService.generarSiguienteRonda(this.torneoId).subscribe({
      next: (partidos) => {
        this.procesando.set(false);
        this.mostrarExito(partidos.length > 0 ? 'Siguiente ronda generada' : 'El torneo finalizó');
        this.cargarTodo();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al generar la siguiente ronda');
        this.procesando.set(false);
      }
    });
  }

  editarResultado(partido: PartidoTorneoResponse) {
    this.editandoResultadoId.set(partido.id);
    this.resultadoForm.reset({ setsPareja1: 0, setsPareja2: 0 });
  }

  cancelarEdicionResultado() {
    this.editandoResultadoId.set(null);
  }

  guardarResultado(partidoId: number) {
    if (this.resultadoForm.invalid) {
      this.resultadoForm.markAllAsTouched();
      this.mostrarError('Ingresá un resultado válido para ambas parejas.');
      return;
    }
    this.procesando.set(true);
    this.torneoService.cargarResultado(this.torneoId, partidoId, this.resultadoForm.value).subscribe({
      next: () => {
        this.procesando.set(false);
        this.editandoResultadoId.set(null);
        this.mostrarExito('Resultado cargado');
        this.cargarPartidos();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al cargar el resultado');
        this.procesando.set(false);
      }
    });
  }

  esMiInscripcion(inscripcion: InscripcionTorneoResponse): boolean {
    const email = this.currentUser()?.email;
    return !!email && (inscripcion.jugador1Email === email || inscripcion.jugador2Email === email);
  }

  esAdmin(): boolean {
    return this.currentUser()?.rol === 'ADMIN';
  }

  esAdminORecepcion(): boolean {
    return this.currentUser()?.rol === 'ADMIN' || this.currentUser()?.rol === 'RECEPCIONISTA';
  }

  ultimaRonda(): number {
    const partidos = this.partidos();
    if (partidos.length === 0) return 0;
    return Math.max(...partidos.map(p => p.numeroRonda));
  }

  partidosDeUltimaRonda(): PartidoTorneoResponse[] {
    const ultima = this.ultimaRonda();
    return this.partidos().filter(p => p.numeroRonda === ultima);
  }

  todosLosPartidosDeLaUltimaRondaFinalizados(): boolean {
    const partidos = this.partidosDeUltimaRonda();
    return partidos.length > 0 && partidos.every(p => p.estado === 'FINALIZADO');
  }

  invalidoInscripcion(campo: string): boolean {
    const control = this.inscripcionForm.get(campo);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  invalidoResultado(campo: string): boolean {
    const control = this.resultadoForm.get(campo);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  claseEstadoPartido(estado: string): string {
    return estado === 'FINALIZADO'
      ? 'bg-emerald-500/10 text-emerald-400'
      : 'bg-amber-500/10 text-amber-400';
  }
}
