import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin, timer } from 'rxjs';
import { MatchService } from '../../../core/services/match.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  CandidatoMatchResponse,
  MatchParejaResponse,
  PerfilJugadorResponse,
  TipoSwipe,
  TurnoSugeridoResponse,
  describirBloques,
  etiquetaPosicion
} from '../../../shared/models/match.model';
import { TarjetaJugadorComponent } from '../tarjeta-jugador/tarjeta-jugador.component';

type EstadoPerfil = 'cargando' | 'sin-perfil' | 'oculto' | 'listo' | 'error';
type Vista = 'descubrir' | 'matches';

// Distancia (px) que hay que arrastrar la carta para que cuente como respuesta.
const UMBRAL_SWIPE = 110;
const DURACION_SALIDA_MS = 280;

@Component({
  selector: 'app-match',
  standalone: true,
  imports: [CommonModule, RouterLink, TarjetaJugadorComponent],
  templateUrl: './match.component.html'
})
export class MatchComponent implements OnInit {
  private matchService = inject(MatchService);
  private authService = inject(AuthService);
  private toast = inject(ToastService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  readonly describirBloques = describirBloques;
  readonly etiquetaPosicion = etiquetaPosicion;

  vista = signal<Vista>('descubrir');
  estadoPerfil = signal<EstadoPerfil>('cargando');
  miPerfil = signal<PerfilJugadorResponse | null>(null);

  // Mazo
  candidatos = signal<CandidatoMatchResponse[]>([]);
  cargandoMazo = signal(false);
  respondiendo = signal(false);
  salida = signal<TipoSwipe | null>(null);
  arrastrando = signal(false);
  dragX = signal(0);
  private inicioX = 0;

  actual = computed(() => this.candidatos()[0] ?? null);
  siguiente = computed(() => this.candidatos()[1] ?? null);

  transformacion = computed(() => {
    const salida = this.salida();
    if (salida === 'LIKE') return 'translateX(140%) rotate(20deg)';
    if (salida === 'PASS') return 'translateX(-140%) rotate(-20deg)';
    const dx = this.dragX();
    return `translateX(${dx}px) rotate(${dx / 16}deg)`;
  });
  transicion = computed(() => this.arrastrando() ? 'none' : `transform ${DURACION_SALIDA_MS}ms ease-out`);
  opacidadLike = computed(() => this.salida() === 'LIKE' ? 1 : Math.min(1, Math.max(0, this.dragX() / UMBRAL_SWIPE)));
  opacidadPass = computed(() => this.salida() === 'PASS' ? 1 : Math.min(1, Math.max(0, -this.dragX() / UMBRAL_SWIPE)));

  // Match nuevo (modal)
  matchNuevo = signal<MatchParejaResponse | null>(null);
  misIniciales = computed(() => {
    const u = this.authService.currentUser();
    return u ? `${u.nombre.charAt(0)}${u.apellido.charAt(0)}`.toUpperCase() : '';
  });

  // Mis matches y reservar juntos
  parejas = signal<MatchParejaResponse[]>([]);
  cargandoParejas = signal(false);
  parejaAbierta = signal<number | null>(null);
  turnos = signal<TurnoSugeridoResponse[]>([]);
  cargandoTurnos = signal(false);
  turnoElegido = signal<TurnoSugeridoResponse | null>(null);
  reservando = signal(false);

  turnosPorDia = computed(() => {
    const grupos = new Map<string, TurnoSugeridoResponse[]>();
    for (const t of this.turnos()) {
      grupos.set(t.fecha, [...(grupos.get(t.fecha) ?? []), t]);
    }
    return [...grupos.entries()].map(([fecha, turnos]) => ({ fecha, turnos }));
  });

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('vista') === 'matches') {
      this.vista.set('matches');
    }
    this.cargarPerfil();
    this.cargarParejas();
  }

  // ---------- Perfil y mazo ----------

  cargarPerfil(): void {
    this.estadoPerfil.set('cargando');
    this.matchService.obtenerMiPerfil().subscribe({
      next: (perfil) => {
        this.miPerfil.set(perfil);
        if (perfil.visibleEnMatch) {
          this.estadoPerfil.set('listo');
          this.cargarMazo();
        } else {
          this.estadoPerfil.set('oculto');
        }
      },
      error: (err: HttpErrorResponse) => {
        // 404 = todavía no armó su perfil; cualquier otro error permite reintentar.
        this.estadoPerfil.set(err.status === 404 ? 'sin-perfil' : 'error');
      }
    });
  }

  cargarMazo(): void {
    this.cargandoMazo.set(true);
    this.matchService.obtenerCandidatos().subscribe({
      next: (lista) => {
        this.candidatos.set(lista);
        this.cargandoMazo.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoMazo.set(false);
        this.toast.error(err.error?.message || 'No se pudieron cargar los jugadores.');
      }
    });
  }

  responder(tipo: TipoSwipe): void {
    const candidato = this.actual();
    if (!candidato || this.respondiendo()) return;
    this.respondiendo.set(true);
    this.salida.set(tipo);

    // Se espera la animación de salida y la respuesta del servidor antes de sacar la carta.
    forkJoin({ res: this.matchService.responder({ usuarioId: candidato.usuarioId, tipo }), anim: timer(DURACION_SALIDA_MS) })
      .subscribe({
        next: ({ res }) => {
          this.sacarPrimera();
          if (res.match && res.pareja) {
            const pareja = res.pareja;
            this.matchNuevo.set(pareja);
            this.parejas.update(lista => [pareja, ...lista.filter(p => p.id !== pareja.id)]);
          }
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 409 || err.status === 404) {
            // Ya respondido o ya no disponible: la carta igual sale del mazo.
            this.sacarPrimera();
          } else {
            this.reiniciarCarta();
          }
          this.toast.error(err.error?.message || 'No se pudo registrar tu respuesta.');
        }
      });
  }

  private sacarPrimera(): void {
    this.candidatos.update(lista => lista.slice(1));
    this.reiniciarCarta();
    if (this.candidatos().length === 0) {
      this.cargarMazo();
    }
  }

  private reiniciarCarta(): void {
    this.salida.set(null);
    this.dragX.set(0);
    this.respondiendo.set(false);
  }

  onPointerDown(evento: PointerEvent): void {
    if (this.respondiendo()) return;
    this.arrastrando.set(true);
    this.inicioX = evento.clientX;
    (evento.currentTarget as HTMLElement).setPointerCapture(evento.pointerId);
  }

  onPointerMove(evento: PointerEvent): void {
    if (!this.arrastrando()) return;
    this.dragX.set(evento.clientX - this.inicioX);
  }

  onPointerUp(): void {
    if (!this.arrastrando()) return;
    this.arrastrando.set(false);
    const dx = this.dragX();
    if (dx > UMBRAL_SWIPE) {
      this.responder('LIKE');
    } else if (dx < -UMBRAL_SWIPE) {
      this.responder('PASS');
    } else {
      this.dragX.set(0);
    }
  }

  @HostListener('window:keydown', ['$event'])
  onTecla(evento: KeyboardEvent): void {
    if (this.vista() !== 'descubrir' || this.matchNuevo()) return;
    const destino = evento.target as HTMLElement | null;
    if (destino && ['INPUT', 'TEXTAREA', 'SELECT'].includes(destino.tagName)) return;
    if (evento.key === 'ArrowRight') this.responder('LIKE');
    if (evento.key === 'ArrowLeft') this.responder('PASS');
  }

  // ---------- Matches ----------

  cargarParejas(): void {
    this.cargandoParejas.set(true);
    this.matchService.obtenerMisParejas().subscribe({
      next: (lista) => {
        this.parejas.set(lista);
        this.cargandoParejas.set(false);
      },
      error: () => this.cargandoParejas.set(false)
    });
  }

  cambiarVista(vista: Vista): void {
    this.vista.set(vista);
    if (vista === 'matches') {
      this.cargarParejas();
    }
  }

  verTurnosDelMatch(pareja: MatchParejaResponse): void {
    this.matchNuevo.set(null);
    this.vista.set('matches');
    this.abrirTurnos(pareja);
  }

  abrirTurnos(pareja: MatchParejaResponse): void {
    if (this.parejaAbierta() === pareja.id) {
      this.parejaAbierta.set(null);
      return;
    }
    this.parejaAbierta.set(pareja.id);
    this.turnoElegido.set(null);
    this.turnos.set([]);
    this.cargandoTurnos.set(true);
    this.matchService.obtenerTurnosSugeridos(pareja.id, 7).subscribe({
      next: (lista) => {
        this.turnos.set(lista);
        this.cargandoTurnos.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoTurnos.set(false);
        this.toast.error(err.error?.message || 'No se pudieron buscar turnos.');
      }
    });
  }

  confirmarReserva(pareja: MatchParejaResponse): void {
    const turno = this.turnoElegido();
    if (!turno || this.reservando()) return;
    this.reservando.set(true);
    this.matchService.reservarJuntos(pareja.id, { canchaId: turno.canchaId, franjaId: turno.franjaId, fecha: turno.fecha })
      .subscribe({
        next: (reserva) => {
          this.reservando.set(false);
          this.toast.success(`Turno reservado. Le avisamos a ${pareja.nombre.split(' ')[0]}.`);
          this.router.navigate(['/pagos/checkout', reserva.id]);
        },
        error: (err: HttpErrorResponse) => {
          this.reservando.set(false);
          this.turnoElegido.set(null);
          this.toast.error(err.error?.message || 'No se pudo reservar el turno.');
          // El turno pudo haberse ocupado: se refrescan las sugerencias.
          this.parejaAbierta.set(null);
          this.abrirTurnos(pareja);
        }
      });
  }

  esTurnoElegido(t: TurnoSugeridoResponse): boolean {
    const e = this.turnoElegido();
    return !!e && e.fecha === t.fecha && e.franjaId === t.franjaId && e.canchaId === t.canchaId;
  }

  // "Sábado 18/10" sin depender del locale de Angular (la app no registra es-AR).
  etiquetaFecha(fechaIso: string): string {
    const fecha = new Date(`${fechaIso}T00:00:00`);
    const dias = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
    const dd = String(fecha.getDate()).padStart(2, '0');
    const mm = String(fecha.getMonth() + 1).padStart(2, '0');
    return `${dias[fecha.getDay()]} ${dd}/${mm}`;
  }

  linkWhatsapp(pareja: MatchParejaResponse): string | null {
    const digitos = (pareja.telefono ?? '').replace(/\D/g, '');
    if (!digitos) return null;
    // Los teléfonos se cargan sin código de país: se asume Argentina (+54).
    const numero = digitos.startsWith('54') ? digitos : `54${digitos}`;
    const texto = encodeURIComponent(`¡Hola ${pareja.nombre.split(' ')[0]}! Hicimos match en PadelApp. ¿Armamos un partido?`);
    return `https://wa.me/${numero}?text=${texto}`;
  }
}
