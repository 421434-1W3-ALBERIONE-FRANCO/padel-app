import { Component, DestroyRef, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TorneoService } from '../../../core/services/torneo.service';
import { AuthService } from '../../../core/services/auth.service';
import { InscripcionTorneoResponse, PartidoTorneoResponse, TorneoResponse } from '../../../shared/models/torneo.model';
import { ToastService } from '../../../core/services/toast.service';
import { CuadroEliminacionComponent } from '../cuadro-eliminacion/cuadro-eliminacion.component';
import { TablaLigaComponent } from '../tabla-liga/tabla-liga.component';
import { construirCuadro, finalJugada } from '../utils/cuadro-eliminacion.util';
import { calcularTablaLiga } from '../utils/tabla-liga.util';
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
  jugadoresDePareja,
  nombreCategoria,
  numeroCategoria
} from '../utils/torneo-visual.util';

type Pestana = 'juego' | 'parejas';
type CampoSets = 'setsPareja1' | 'setsPareja2';

interface Campeon {
  inscripcionId: number;
  jugadores: string[];
}

@Component({
  selector: 'app-torneo-detalle',
  imports: [CommonModule, RouterLink, ReactiveFormsModule, FormsModule, CuadroEliminacionComponent, TablaLigaComponent],
  templateUrl: './torneo-detalle.component.html'
})
export class TorneoDetalleComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private toast = inject(ToastService);
  private torneoService = inject(TorneoService);
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
  readonly jugadoresDePareja = jugadoresDePareja;
  readonly nombreCategoria = nombreCategoria;
  readonly numeroCategoria = numeroCategoria;

  currentUser = this.authService.currentUser;
  torneoId = Number(this.route.snapshot.paramMap.get('id'));

  torneo = signal<TorneoResponse | null>(null);
  inscripciones = signal<InscripcionTorneoResponse[]>([]);
  partidos = signal<PartidoTorneoResponse[]>([]);

  pestana = signal<Pestana>('juego');
  cargando = signal<boolean>(true);
  procesando = signal<boolean>(false);
  private primeraCarga = true;

  // Reloj de la cuenta regresiva: con minutos de resolución alcanza un tic cada 30 s.
  ahora = signal(new Date());

  esLiga = computed(() => this.torneo()?.formato === 'LIGA_TODOS_CONTRA_TODOS');
  confirmadas = computed(() => this.inscripciones().filter(i => i.estado === 'CONFIRMADA'));
  /** Las confirmadas primero; las invitaciones sin responder (solo las ven los involucrados y el admin) al final. */
  inscripcionesOrdenadas = computed(() => [
    ...this.confirmadas(),
    ...this.inscripciones().filter(i => i.estado !== 'CONFIRMADA')
  ]);
  /** Invitaciones que me hicieron y todavía no respondí. */
  invitacionesRecibidas = computed(() => {
    const id = this.currentUser()?.id;
    return this.inscripciones().filter(i => i.estado === 'PENDIENTE_ACEPTACION' && i.jugador2Id === id);
  });
  /** La invitación que mandé y espera respuesta: mientras tanto no puedo invitar a otra persona. */
  invitacionEnviada = computed(() => {
    const id = this.currentUser()?.id;
    return this.inscripciones().find(i => i.estado === 'PENDIENTE_ACEPTACION' && i.jugador1Id === id) ?? null;
  });
  miInscripcion = computed(() => {
    const id = this.currentUser()?.id;
    return id == null ? null : this.confirmadas().find(i => i.jugador1Id === id || i.jugador2Id === id) ?? null;
  });
  miInscripcionId = computed(() => this.miInscripcion()?.id ?? null);
  companero = computed(() => {
    const inscripcion = this.miInscripcion();
    if (!inscripcion) return null;
    return inscripcion.jugador1Id === this.currentUser()?.id ? inscripcion.jugador2Nombre : inscripcion.jugador1Nombre;
  });

  /** En eliminación directa, la pareja que ganó la final; en una liga terminada, la primera de la tabla. */
  campeon = computed<Campeon | null>(() => {
    const torneo = this.torneo();
    if (!torneo) return null;
    if (this.esLiga()) {
      if (torneo.estado !== 'FINALIZADO') return null;
      const primera = calcularTablaLiga(this.partidos())[0];
      return primera ? { inscripcionId: primera.inscripcionId, jugadores: jugadoresDePareja(primera.nombres) } : null;
    }
    const final = finalJugada(construirCuadro(this.partidos()));
    if (!final || final.ganadorInscripcionId == null) return null;
    const nombres = final.ganadorInscripcionId === final.inscripcion1Id ? final.inscripcion1Nombres : final.inscripcion2Nombres;
    return { inscripcionId: final.ganadorInscripcionId, jugadores: jugadoresDePareja(nombres) };
  });
  campeonEsMio = computed(() => {
    const campeon = this.campeon();
    return !!campeon && campeon.inscripcionId === this.miInscripcionId();
  });

  cuenta = computed(() => {
    const torneo = this.torneo();
    return torneo?.estado === 'INSCRIPCION_ABIERTA' ? cuentaRegresiva(torneo.fechaInicio, this.ahora()) : null;
  });
  cupo = computed(() => {
    const torneo = this.torneo();
    return torneo && torneo.maxParejas > 0 ? Math.min(100, Math.round(torneo.parejasInscriptas / torneo.maxParejas * 100)) : 0;
  });
  // Los pases directos (sin rival) no cuentan como partidos.
  partidosConRival = computed(() => this.partidos().filter(p => p.inscripcion2Id != null));
  partidosJugados = computed(() => this.partidosConRival().filter(p => p.estado === 'FINALIZADO').length);
  puedeCargarResultados = computed(() => this.esAdminORecepcion() && this.torneo()?.estado === 'EN_CURSO');

  // Carga de resultados (modal compartido por el cuadro y la liga)
  partidoEnCarga = signal<PartidoTorneoResponse | null>(null);

  inscripcionForm: FormGroup = this.fb.group({
    companeroEmail: ['', [Validators.required, Validators.email]]
  });

  resultadoForm: FormGroup = this.fb.group({
    setsPareja1: [0, [Validators.required, Validators.min(0), Validators.max(9)]],
    setsPareja2: [0, [Validators.required, Validators.min(0), Validators.max(9)]]
  });

  constructor() {
    const reloj = setInterval(() => this.ahora.set(new Date()), 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(reloj));
  }

  ngOnInit() {
    this.cargarTodo();
  }

  cargarTodo() {
    this.cargando.set(true);

    this.torneoService.obtenerPorId(this.torneoId).subscribe({
      next: (data) => {
        this.torneo.set(data);
        if (this.primeraCarga) {
          // Con la inscripción abierta lo útil es anotarse; después, seguir el cuadro o la tabla.
          this.pestana.set(data.estado === 'INSCRIPCION_ABIERTA' ? 'parejas' : 'juego');
          this.primeraCarga = false;
        }
        this.cargarInscripciones();
        this.cargarPartidos();
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Error al cargar el torneo');
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

  setPestana(pestana: Pestana) {
    this.pestana.set(pestana);
  }

  private mostrarError(msg: string) {
    this.toast.error(msg);
  }

  private mostrarExito(msg: string) {
    this.toast.success(msg);
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
        this.mostrarExito('Invitación enviada: la pareja queda inscripta cuando tu compañero/a la acepte.');
        this.cargarTodo();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al inscribir la pareja');
        this.procesando.set(false);
      }
    });
  }

  aceptarInvitacion(inscripcionId: number) {
    this.procesando.set(true);
    this.torneoService.aceptarInvitacion(this.torneoId, inscripcionId).subscribe({
      next: () => {
        this.procesando.set(false);
        this.mostrarExito('¡Listo! Quedaron inscriptos.');
        this.cargarTodo();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'No se pudo aceptar la invitación');
        this.procesando.set(false);
        this.cargarInscripciones();
      }
    });
  }

  rechazarInvitacion(inscripcionId: number) {
    if (!confirm('¿Rechazar la invitación? Le avisamos a quien te invitó.')) return;
    this.procesando.set(true);
    this.torneoService.rechazarInvitacion(this.torneoId, inscripcionId).subscribe({
      next: () => {
        this.procesando.set(false);
        this.mostrarExito('Invitación rechazada');
        this.cargarInscripciones();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'No se pudo rechazar la invitación');
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
        // También el torneo: el cupo del encabezado cambia.
        this.cargarTodo();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al cancelar la inscripción');
        this.procesando.set(false);
      }
    });
  }

  // --- Suspensión de torneo (Fase 2) ---
  mostrarModalSuspension = signal<boolean>(false);
  motivoSuspension = '';

  abrirSuspension() {
    this.motivoSuspension = '';
    this.mostrarModalSuspension.set(true);
  }

  cerrarSuspension() {
    this.mostrarModalSuspension.set(false);
  }

  confirmarSuspension() {
    this.procesando.set(true);
    this.torneoService.suspender(this.torneoId, this.motivoSuspension).subscribe({
      next: () => {
        this.procesando.set(false);
        this.cerrarSuspension();
        this.mostrarExito('Torneo suspendido');
        this.cargarTodo();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al suspender el torneo');
        this.procesando.set(false);
      }
    });
  }

  reactivarTorneo() {
    this.procesando.set(true);
    this.torneoService.reactivar(this.torneoId).subscribe({
      next: () => {
        this.procesando.set(false);
        this.mostrarExito('Torneo reactivado');
        this.cargarTodo();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al reactivar el torneo');
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
        this.mostrarExito(this.esLiga() ? 'Fixture generado: ya se pueden cargar los resultados' : 'Cuadro sorteado');
        this.pestana.set('juego');
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

  // --- Resultados ---

  abrirCargaResultado(partido: PartidoTorneoResponse) {
    this.resultadoForm.reset({ setsPareja1: 0, setsPareja2: 0 });
    this.partidoEnCarga.set(partido);
  }

  cerrarCargaResultado() {
    this.partidoEnCarga.set(null);
  }

  ajustarSets(campo: CampoSets, delta: number) {
    const control = this.resultadoForm.get(campo);
    if (!control) return;
    const valor = Math.max(0, Math.min(9, (Number(control.value) || 0) + delta));
    control.setValue(valor);
    control.markAsDirty();
  }

  empate(): boolean {
    const { setsPareja1, setsPareja2 } = this.resultadoForm.value;
    return Number(setsPareja1) === Number(setsPareja2);
  }

  /** Quién gana con los sets cargados hasta ahora (1, 2 o null si están iguales). */
  ganadorParcial(): 1 | 2 | null {
    const { setsPareja1, setsPareja2 } = this.resultadoForm.value;
    if (Number(setsPareja1) === Number(setsPareja2)) return null;
    return Number(setsPareja1) > Number(setsPareja2) ? 1 : 2;
  }

  guardarResultado() {
    const partido = this.partidoEnCarga();
    if (!partido) return;
    if (this.resultadoForm.invalid) {
      this.resultadoForm.markAllAsTouched();
      this.mostrarError('Ingresá los sets de las dos parejas (de 0 a 9).');
      return;
    }
    if (this.empate()) {
      this.mostrarError('El resultado no puede terminar en empate.');
      return;
    }
    this.procesando.set(true);
    const { setsPareja1, setsPareja2 } = this.resultadoForm.value;
    this.torneoService.cargarResultado(this.torneoId, partido.id, { setsPareja1: Number(setsPareja1), setsPareja2: Number(setsPareja2) }).subscribe({
      next: () => {
        this.procesando.set(false);
        this.partidoEnCarga.set(null);
        this.mostrarExito('Resultado cargado');
        // Se recarga también el torneo: con el último resultado de una liga pasa a FINALIZADO.
        this.cargarTodo();
      },
      error: (err) => {
        this.mostrarError(err.error?.message || 'Error al cargar el resultado');
        this.procesando.set(false);
      }
    });
  }

  @HostListener('document:keydown.escape')
  cerrarModales() {
    if (this.procesando()) return;
    this.partidoEnCarga.set(null);
    this.mostrarModalSuspension.set(false);
  }

  esMiInscripcion(inscripcion: InscripcionTorneoResponse): boolean {
    const id = this.currentUser()?.id;
    return id != null && (inscripcion.jugador1Id === id || inscripcion.jugador2Id === id);
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

  /** Con la final jugada, "siguiente ronda" cierra el torneo en el backend. */
  ultimaRondaEsFinal(): boolean {
    return this.partidosDeUltimaRonda().length === 1;
  }

  invalidoInscripcion(campo: string): boolean {
    const control = this.inscripcionForm.get(campo);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  iniciales(nombre: string): string {
    return nombre.split(/\s+/).filter(Boolean).slice(0, 2).map(p => p.charAt(0)).join('').toUpperCase();
  }

  /** "$ 12.000" con el separador de miles argentino (Intl del navegador: la app no registra es-AR en Angular). */
  precio(valor: number): string {
    return valor > 0 ? `$ ${valor.toLocaleString('es-AR', { maximumFractionDigits: 0 })}` : 'Gratis';
  }

  lugaresLibres(torneo: TorneoResponse): string {
    const libres = Math.max(0, torneo.maxParejas - torneo.parejasInscriptas);
    if (libres === 0) return 'Cupo completo';
    return libres === 1 ? 'Queda 1 lugar' : `Quedan ${libres} lugares`;
  }
}
