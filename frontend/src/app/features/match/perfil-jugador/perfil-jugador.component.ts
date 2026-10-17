import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatchService } from '../../../core/services/match.service';
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

@Component({
  selector: 'app-perfil-jugador',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DisponibilidadGridComponent],
  templateUrl: './perfil-jugador.component.html'
})
export class PerfilJugadorComponent implements OnInit {
  private matchService = inject(MatchService);
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

  ngOnInit(): void {
    this.matchService.obtenerMiPerfil().subscribe({
      next: (perfil) => {
        this.categoria.set(perfil.categoria);
        this.posicion.set(perfil.posicion);
        this.bio.set(perfil.bio ?? '');
        this.visibleEnMatch.set(perfil.visibleEnMatch);
        this.disponibilidad.set(perfil.disponibilidad);
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
        this.guardando.set(false);
        this.toast.success('Perfil guardado.');
        // Al crearlo por primera vez (o si vino desde el match) se vuelve al mazo.
        if (this.esNuevo() || this.route.snapshot.queryParamMap.get('volver') === 'match') {
          this.router.navigate(['/match']);
        }
        this.esNuevo.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.toast.error(err.error?.message || 'No se pudo guardar tu perfil.');
      }
    });
  }

  private ordenar(bloques: BloqueDisponible[]): BloqueDisponible[] {
    const ordenDia = (d: DiaSemana) => DIAS_SEMANA.findIndex(x => x.valor === d);
    const ordenBloque = (b: BloqueHorario) => ['MANANA', 'TARDE', 'NOCHE'].indexOf(b);
    return [...bloques].sort((a, b) => ordenDia(a.diaSemana) - ordenDia(b.diaSemana) || ordenBloque(a.bloque) - ordenBloque(b.bloque));
  }
}
