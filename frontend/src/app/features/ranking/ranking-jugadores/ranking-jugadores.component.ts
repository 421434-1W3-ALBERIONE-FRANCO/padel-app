import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { RankingService } from '../../../core/services/ranking.service';
import { AuthService } from '../../../core/services/auth.service';
import { CategoriaJugador } from '../../../shared/models/torneo.model';
import { RankingJugadorResponse } from '../../../shared/models/ranking.model';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-ranking-jugadores',
  imports: [RouterLink, FormsModule],
  templateUrl: './ranking-jugadores.component.html'
})
export class RankingJugadoresComponent implements OnInit {
  private rankingService = inject(RankingService);
  private toast = inject(ToastService);
  private authService = inject(AuthService);

  currentUser = this.authService.currentUser;

  categorias: CategoriaJugador[] = ['PRIMERA', 'SEGUNDA', 'TERCERA', 'CUARTA', 'QUINTA', 'SEXTA', 'SEPTIMA', 'OCTAVA'];
  categoriaSeleccionada = signal<CategoriaJugador>('CUARTA');

  ranking = signal<RankingJugadorResponse[]>([]);
  cargando = signal<boolean>(true);

  ngOnInit() {
    this.cargarRanking();
  }

  cambiarCategoria(categoria: CategoriaJugador) {
    this.categoriaSeleccionada.set(categoria);
    this.cargarRanking();
  }

  cargarRanking() {
    this.cargando.set(true);
    this.rankingService.obtenerRankingPorCategoria(this.categoriaSeleccionada()).subscribe({
      next: (data) => {
        this.ranking.set(data);
        this.cargando.set(false);
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Error al cargar el ranking');
        this.cargando.set(false);
      }
    });
  }

  esMiFila(fila: RankingJugadorResponse): boolean {
    return fila.jugadorId === this.currentUser()?.id;
  }
}
