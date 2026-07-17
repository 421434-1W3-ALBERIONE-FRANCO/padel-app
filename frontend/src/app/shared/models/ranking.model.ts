import { CategoriaJugador } from './torneo.model';

export interface RankingJugadorResponse {
  id: number;
  jugadorId: number;
  jugadorNombre: string;
  jugadorEmail: string;
  categoria: CategoriaJugador;
  puntos: number;
  partidosJugados: number;
  partidosGanados: number;
}
