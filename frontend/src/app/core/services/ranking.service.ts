import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CategoriaJugador } from '../../shared/models/torneo.model';
import { RankingJugadorResponse } from '../../shared/models/ranking.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class RankingService {
  private http = inject(HttpClient);
  private readonly API_URL = environment.apiUrl;

  obtenerRankingPorCategoria(categoria: CategoriaJugador): Observable<RankingJugadorResponse[]> {
    return this.http.get<RankingJugadorResponse[]>(`${this.API_URL}/rankings/${categoria}`);
  }

  obtenerMiPosicion(categoria: CategoriaJugador): Observable<RankingJugadorResponse> {
    return this.http.get<RankingJugadorResponse>(`${this.API_URL}/rankings/${categoria}/mi-posicion`);
  }
}
