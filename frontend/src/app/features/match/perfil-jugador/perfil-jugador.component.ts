import { Component, ElementRef, OnDestroy, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatchService } from '../../../core/services/match.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { CategoriaJugador } from '../../../shared/models/torneo.model';
import {
  BloqueDisponible,
  BloqueHorario,
  DIAS_SEMANA,
  POSICIONES,
  PosicionJuego
} from '../../../shared/models/match.model';
import { DiaSemana } from '../../../shared/models/control-horario.model';
import { DisponibilidadGridComponent } from '../disponibilidad-grid/disponibilidad-grid.component';
import { EditorFotoComponent, FuenteFoto } from '../editor-foto/editor-foto.component';
import { AvatarJugadorComponent } from '../../../shared/components/avatar-jugador/avatar-jugador.component';

@Component({
  selector: 'app-perfil-jugador',
  imports: [CommonModule, FormsModule, RouterLink, DisponibilidadGridComponent, EditorFotoComponent, AvatarJugadorComponent],
  templateUrl: './perfil-jugador.component.html'
})
export class PerfilJugadorComponent implements OnInit, OnDestroy {
  private matchService = inject(MatchService);
  private authService = inject(AuthService);
  private toast = inject(ToastService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  readonly categorias: CategoriaJugador[] = ['PRIMERA', 'SEGUNDA', 'TERCERA', 'CUARTA', 'QUINTA', 'SEXTA', 'SEPTIMA', 'OCTAVA'];
  readonly posiciones = POSICIONES;
  readonly BIO_MAX = 280;

  cargando = signal(true);
  guardando = signal(false);
  esNuevo = signal(false);
  intentoGuardar = signal(false);

  categoria = signal<CategoriaJugador>('CUARTA');
  posicion = signal<PosicionJuego>('INDISTINTO');
  bio = signal('');
  visibleEnMatch = signal(true);
  disponibilidad = signal<BloqueDisponible[]>([]);

  sinHorarios = computed(() => this.disponibilidad().length === 0);

  // Foto de perfil: una sola. Con el perfil ya creado se sube en el acto; si es nuevo, queda pendiente
  // y se sube después de guardarlo (el backend la asocia al perfil).
  private inputArchivo = viewChild<ElementRef<HTMLInputElement>>('inputArchivo');
  private inputCamara = viewChild<ElementRef<HTMLInputElement>>('inputCamara');
  usuarioId = computed(() => this.authService.currentUser()?.id ?? null);
  iniciales = computed(() => {
    const u = this.authService.currentUser();
    return u ? `${u.nombre.charAt(0)}${u.apellido.charAt(0)}`.toUpperCase() : '';
  });
  fotoVersion = signal<number | null>(null);
  fotoPendiente = signal<Blob | null>(null);
  fotoPendienteUrl = signal<string | null>(null);
  fuenteEditor = signal<FuenteFoto | null>(null);
  subiendoFoto = signal(false);
  tieneFoto = computed(() => this.fotoVersion() != null || this.fotoPendiente() != null);

  ngOnInit(): void {
    this.matchService.obtenerMiPerfil().subscribe({
      next: (perfil) => {
        this.categoria.set(perfil.categoria);
        this.posicion.set(perfil.posicion);
        this.bio.set(perfil.bio ?? '');
        this.visibleEnMatch.set(perfil.visibleEnMatch);
        this.disponibilidad.set(perfil.disponibilidad);
        this.fotoVersion.set(perfil.fotoVersion ?? null);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        // 404 = todavía no tiene perfil: se muestra el formulario vacío para crearlo.
        if (err.status === 404) {
          this.esNuevo.set(true);
        } else {
          this.toast.error(err.error?.message || 'No se pudo cargar tu perfil de jugador.');
        }
        this.cargando.set(false);
      }
    });
  }

  aplicarAtajo(atajo: 'noches' | 'finde' | 'limpiar'): void {
    const semana: DiaSemana[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
    const finde: DiaSemana[] = ['SATURDAY', 'SUNDAY'];
    const agregar = (dias: DiaSemana[], bloques: BloqueHorario[]) => {
      const actual = this.disponibilidad();
      const nuevos = dias.flatMap(d => bloques.map(b => ({ diaSemana: d, bloque: b })))
        .filter(n => !actual.some(a => a.diaSemana === n.diaSemana && a.bloque === n.bloque));
      this.disponibilidad.set([...actual, ...nuevos]);
    };
    if (atajo === 'noches') agregar(semana, ['NOCHE']);
    if (atajo === 'finde') agregar(finde, ['MANANA', 'TARDE']);
    if (atajo === 'limpiar') this.disponibilidad.set([]);
  }

  guardar(): void {
    this.intentoGuardar.set(true);
    if (this.sinHorarios()) {
      this.toast.error('Marcá al menos un horario en el que puedas jugar.');
      return;
    }
    if (this.bio().length > this.BIO_MAX) {
      this.toast.error(`La presentación no puede superar los ${this.BIO_MAX} caracteres.`);
      return;
    }
    this.guardando.set(true);
    this.matchService.guardarMiPerfil({
      categoria: this.categoria(),
      posicion: this.posicion(),
      bio: this.bio().trim() || null,
      visibleEnMatch: this.visibleEnMatch(),
      disponibilidad: this.ordenar(this.disponibilidad())
    }).subscribe({
      next: () => {
        const pendiente = this.fotoPendiente();
        if (pendiente) {
          // Perfil nuevo con foto elegida: recién ahora existe el perfil al que se asocia.
          this.matchService.subirFoto(pendiente).subscribe({
            next: (perfil) => {
              this.descartarPendiente();
              this.fotoVersion.set(perfil.fotoVersion ?? null);
              this.terminarGuardado();
            },
            error: (err: HttpErrorResponse) => {
              this.toast.error(err.error?.message || 'El perfil se guardó, pero no se pudo subir la foto. Probá de nuevo.');
              this.descartarPendiente();
              this.guardando.set(false);
              this.esNuevo.set(false);
            }
          });
          return;
        }
        this.terminarGuardado();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.toast.error(err.error?.message || 'No se pudo guardar tu perfil.');
      }
    });
  }

  private terminarGuardado(): void {
    this.guardando.set(false);
    this.toast.success('Perfil guardado.');
    // Al crearlo por primera vez (o si vino desde el match) se vuelve al mazo.
    if (this.esNuevo() || this.route.snapshot.queryParamMap.get('volver') === 'match') {
      this.router.navigate(['/match']);
    }
    this.esNuevo.set(false);
  }

  // ---------- Foto ----------

  /** En el celular abre la cámara nativa; en la compu, la webcam dentro de la página. */
  tomarFoto(): void {
    if (window.matchMedia?.('(pointer: coarse)').matches) {
      this.inputCamara()?.nativeElement.click();
    } else {
      this.fuenteEditor.set({ tipo: 'camara' });
    }
  }

  elegirArchivo(): void {
    this.inputArchivo()?.nativeElement.click();
  }

  /** La cámara no está disponible: se cierra el editor y se ofrece elegir una foto. */
  cambiarAArchivo(): void {
    this.fuenteEditor.set(null);
    this.elegirArchivo();
  }

  alElegirArchivo(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = ''; // permite volver a elegir el mismo archivo
    if (archivo) {
      this.fuenteEditor.set({ tipo: 'archivo', archivo });
    }
  }

  errorEditor(mensaje: string): void {
    this.fuenteEditor.set(null);
    this.toast.error(mensaje);
  }

  alFotoLista(foto: Blob): void {
    this.fuenteEditor.set(null);
    if (this.esNuevo()) {
      this.descartarPendiente();
      this.fotoPendiente.set(foto);
      this.fotoPendienteUrl.set(URL.createObjectURL(foto));
      return;
    }
    this.subiendoFoto.set(true);
    this.matchService.subirFoto(foto).subscribe({
      next: (perfil) => {
        this.subiendoFoto.set(false);
        this.fotoVersion.set(perfil.fotoVersion ?? null);
        this.toast.success('Foto de perfil actualizada.');
      },
      error: (err: HttpErrorResponse) => {
        this.subiendoFoto.set(false);
        this.toast.error(err.error?.message || 'No se pudo subir la foto.');
      }
    });
  }

  quitarFoto(): void {
    if (this.fotoPendiente()) {
      this.descartarPendiente();
      return;
    }
    this.subiendoFoto.set(true);
    this.matchService.eliminarFoto().subscribe({
      next: () => {
        this.subiendoFoto.set(false);
        this.fotoVersion.set(null);
        this.toast.success('Quitaste tu foto de perfil.');
      },
      error: (err: HttpErrorResponse) => {
        this.subiendoFoto.set(false);
        this.toast.error(err.error?.message || 'No se pudo quitar la foto.');
      }
    });
  }

  private descartarPendiente(): void {
    const url = this.fotoPendienteUrl();
    if (url) URL.revokeObjectURL(url);
    this.fotoPendiente.set(null);
    this.fotoPendienteUrl.set(null);
  }

  ngOnDestroy(): void {
    this.descartarPendiente();
  }

  private ordenar(bloques: BloqueDisponible[]): BloqueDisponible[] {
    const ordenDia = (d: DiaSemana) => DIAS_SEMANA.findIndex(x => x.valor === d);
    const ordenBloque = (b: BloqueHorario) => ['MANANA', 'TARDE', 'NOCHE'].indexOf(b);
    return [...bloques].sort((a, b) => ordenDia(a.diaSemana) - ordenDia(b.diaSemana) || ordenBloque(a.bloque) - ordenBloque(b.bloque));
  }
}
