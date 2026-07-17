import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CategoriaJugador,
  EstadoTorneo,
  InscripcionTorneoRequest,
  InscripcionTorneoResponse,
  PartidoTorneoResponse,
  ResultadoPartidoRequest,
  TipoTorneo,
  TorneoRequest,
  TorneoResponse
} from '../../shared/models/torneo.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class TorneoService {
  private http = inject(HttpClient);
  private readonly API_URL = environment.apiUrl;

  buscar(filtros?: { estado?: EstadoTorneo; categoria?: CategoriaJugador; tipo?: TipoTorneo }): Observable<TorneoResponse[]> {
    let params = new HttpParams();
    if (filtros?.estado) params = params.set('estado', filtros.estado);
    if (filtros?.categoria) params = params.set('categoria', filtros.categoria);
    if (filtros?.tipo) params = params.set('tipo', filtros.tipo);
    return this.http.get<TorneoResponse[]>(`${this.API_URL}/torneos`, { params });
  }

  obtenerPorId(id: number): Observable<TorneoResponse> {
    return this.http.get<TorneoResponse>(`${this.API_URL}/torneos/${id}`);
  }

  crear(request: TorneoRequest): Observable<TorneoResponse> {
    return this.http.post<TorneoResponse>(`${this.API_URL}/torneos`, request);
  }

  actualizar(id: number, request: TorneoRequest): Observable<TorneoResponse> {
    return this.http.put<TorneoResponse>(`${this.API_URL}/torneos/${id}`, request);
  }

  cancelar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/torneos/${id}`);
  }

  inscribirPareja(torneoId: number, request: InscripcionTorneoRequest): Observable<InscripcionTorneoResponse> {
    return this.http.post<InscripcionTorneoResponse>(`${this.API_URL}/torneos/${torneoId}/inscripciones`, request);
  }

  listarInscripciones(torneoId: number): Observable<InscripcionTorneoResponse[]> {
    return this.http.get<InscripcionTorneoResponse[]>(`${this.API_URL}/torneos/${torneoId}/inscripciones`);
  }

  cancelarInscripcion(torneoId: number, inscripcionId: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/torneos/${torneoId}/inscripciones/${inscripcionId}`);
  }

  generarFixture(torneoId: number): Observable<PartidoTorneoResponse[]> {
    return this.http.post<PartidoTorneoResponse[]>(`${this.API_URL}/torneos/${torneoId}/fixture`, {});
  }

  listarPartidos(torneoId: number): Observable<PartidoTorneoResponse[]> {
    return this.http.get<PartidoTorneoResponse[]>(`${this.API_URL}/torneos/${torneoId}/partidos`);
  }

  cargarResultado(torneoId: number, partidoId: number, request: ResultadoPartidoRequest): Observable<PartidoTorneoResponse> {
    return this.http.put<PartidoTorneoResponse>(`${this.API_URL}/torneos/${torneoId}/partidos/${partidoId}/resultado`, request);
  }

  generarSiguienteRonda(torneoId: number): Observable<PartidoTorneoResponse[]> {
    return this.http.post<PartidoTorneoResponse[]>(`${this.API_URL}/torneos/${torneoId}/siguiente-ronda`, {});
  }
}
