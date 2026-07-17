import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CategoriaJugador } from '../../shared/models/torneo.model';
import {
  EstadoSolicitud,
  PostulacionSolicitudRequest,
  SolicitudPartidoRequest,
  SolicitudPartidoResponse,
  TipoSolicitud
} from '../../shared/models/solicitud-partido.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class SolicitudPartidoService {
  private http = inject(HttpClient);
  private readonly API_URL = environment.apiUrl;

  buscar(filtros?: { tipoSolicitud?: TipoSolicitud; categoria?: CategoriaJugador; estado?: EstadoSolicitud }): Observable<SolicitudPartidoResponse[]> {
    let params = new HttpParams();
    if (filtros?.tipoSolicitud) params = params.set('tipoSolicitud', filtros.tipoSolicitud);
    if (filtros?.categoria) params = params.set('categoria', filtros.categoria);
    if (filtros?.estado) params = params.set('estado', filtros.estado);
    return this.http.get<SolicitudPartidoResponse[]>(`${this.API_URL}/solicitudes-partido`, { params });
  }

  obtenerPorId(id: number): Observable<SolicitudPartidoResponse> {
    return this.http.get<SolicitudPartidoResponse>(`${this.API_URL}/solicitudes-partido/${id}`);
  }

  misSolicitudes(): Observable<SolicitudPartidoResponse[]> {
    return this.http.get<SolicitudPartidoResponse[]>(`${this.API_URL}/solicitudes-partido/mis-solicitudes`);
  }

  misPostulaciones(): Observable<SolicitudPartidoResponse[]> {
    return this.http.get<SolicitudPartidoResponse[]>(`${this.API_URL}/solicitudes-partido/mis-postulaciones`);
  }

  crear(request: SolicitudPartidoRequest): Observable<SolicitudPartidoResponse> {
    return this.http.post<SolicitudPartidoResponse>(`${this.API_URL}/solicitudes-partido`, request);
  }

  cancelar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/solicitudes-partido/${id}`);
  }

  postularse(id: number, request: PostulacionSolicitudRequest): Observable<SolicitudPartidoResponse> {
    return this.http.post<SolicitudPartidoResponse>(`${this.API_URL}/solicitudes-partido/${id}/postulaciones`, request);
  }

  aceptarPostulacion(id: number, postulacionId: number): Observable<SolicitudPartidoResponse> {
    return this.http.put<SolicitudPartidoResponse>(`${this.API_URL}/solicitudes-partido/${id}/postulaciones/${postulacionId}/aceptar`, {});
  }

  rechazarPostulacion(id: number, postulacionId: number): Observable<SolicitudPartidoResponse> {
    return this.http.put<SolicitudPartidoResponse>(`${this.API_URL}/solicitudes-partido/${id}/postulaciones/${postulacionId}/rechazar`, {});
  }
}
