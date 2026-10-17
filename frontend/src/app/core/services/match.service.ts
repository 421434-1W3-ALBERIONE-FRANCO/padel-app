import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CandidatoMatchResponse,
  MatchParejaResponse,
  PerfilJugadorRequest,
  PerfilJugadorResponse,
  ReservaMatchRequest,
  SwipeRequest,
  SwipeResponse,
  TurnoSugeridoResponse
} from '../../shared/models/match.model';
import { ReservaResponse } from '../../shared/models/reserva.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class MatchService {
  private http = inject(HttpClient);
  private readonly API_URL = environment.apiUrl;

  obtenerMiPerfil(): Observable<PerfilJugadorResponse> {
    return this.http.get<PerfilJugadorResponse>(`${this.API_URL}/jugadores/me/perfil`);
  }

  guardarMiPerfil(request: PerfilJugadorRequest): Observable<PerfilJugadorResponse> {
    return this.http.put<PerfilJugadorResponse>(`${this.API_URL}/jugadores/me/perfil`, request);
  }

  obtenerCandidatos(limite = 20): Observable<CandidatoMatchResponse[]> {
    const params = new HttpParams().set('limite', limite);
    return this.http.get<CandidatoMatchResponse[]>(`${this.API_URL}/match/candidatos`, { params });
  }

  responder(request: SwipeRequest): Observable<SwipeResponse> {
    return this.http.post<SwipeResponse>(`${this.API_URL}/match/swipes`, request);
  }

  obtenerMisParejas(): Observable<MatchParejaResponse[]> {
    return this.http.get<MatchParejaResponse[]>(`${this.API_URL}/match/parejas`);
  }

  obtenerTurnosSugeridos(matchId: number, dias = 7): Observable<TurnoSugeridoResponse[]> {
    const params = new HttpParams().set('dias', dias);
    return this.http.get<TurnoSugeridoResponse[]>(`${this.API_URL}/match/parejas/${matchId}/turnos-sugeridos`, { params });
  }

  reservarJuntos(matchId: number, request: ReservaMatchRequest): Observable<ReservaResponse> {
    return this.http.post<ReservaResponse>(`${this.API_URL}/match/parejas/${matchId}/reservas`, request);
  }
}
