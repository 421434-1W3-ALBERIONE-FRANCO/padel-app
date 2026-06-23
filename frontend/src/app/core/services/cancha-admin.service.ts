import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CanchaRequest, CanchaResponse } from '../../shared/models/cancha.model';
import { FranjaHorariaRequest, FranjaHorariaResponse } from '../../shared/models/franja.model';
import { BloqueoCanchaRequest, BloqueoCanchaResponse } from '../../shared/models/bloqueo.model';

@Injectable({
  providedIn: 'root'
})
export class CanchaAdminService {
  private http = inject(HttpClient);
  private readonly API_URL = 'http://localhost:8080/api/v1';

  // Canchas CRUD
  getCanchas(): Observable<CanchaResponse[]> {
    return this.http.get<CanchaResponse[]>(`${this.API_URL}/canchas`);
  }

  getCancha(id: number): Observable<CanchaResponse> {
    return this.http.get<CanchaResponse>(`${this.API_URL}/canchas/${id}`);
  }

  crearCancha(cancha: CanchaRequest): Observable<CanchaResponse> {
    return this.http.post<CanchaResponse>(`${this.API_URL}/canchas`, cancha);
  }

  actualizarCancha(id: number, cancha: CanchaRequest): Observable<CanchaResponse> {
    return this.http.put<CanchaResponse>(`${this.API_URL}/canchas/${id}`, cancha);
  }

  desactivarCancha(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/canchas/${id}`);
  }

  // Franjas Horarias CRUD
  getFranjas(canchaId: number): Observable<FranjaHorariaResponse[]> {
    return this.http.get<FranjaHorariaResponse[]>(`${this.API_URL}/canchas/${canchaId}/franjas`);
  }

  getFranja(id: number): Observable<FranjaHorariaResponse> {
    return this.http.get<FranjaHorariaResponse>(`${this.API_URL}/franjas-horarias/${id}`);
  }

  crearFranja(canchaId: number, franja: FranjaHorariaRequest): Observable<FranjaHorariaResponse> {
    return this.http.post<FranjaHorariaResponse>(`${this.API_URL}/canchas/${canchaId}/franjas`, franja);
  }

  actualizarFranja(id: number, franja: FranjaHorariaRequest): Observable<FranjaHorariaResponse> {
    return this.http.put<FranjaHorariaResponse>(`${this.API_URL}/franjas-horarias/${id}`, franja);
  }

  eliminarFranja(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/franjas-horarias/${id}`);
  }

  // Bloqueos CRUD
  getBloqueos(canchaId: number): Observable<BloqueoCanchaResponse[]> {
    return this.http.get<BloqueoCanchaResponse[]>(`${this.API_URL}/canchas/${canchaId}/bloqueos`);
  }

  getBloqueo(id: number): Observable<BloqueoCanchaResponse> {
    return this.http.get<BloqueoCanchaResponse>(`${this.API_URL}/bloqueos/${id}`);
  }

  crearBloqueo(canchaId: number, bloqueo: BloqueoCanchaRequest): Observable<BloqueoCanchaResponse> {
    return this.http.post<BloqueoCanchaResponse>(`${this.API_URL}/canchas/${canchaId}/bloqueos`, bloqueo);
  }

  actualizarBloqueo(id: number, bloqueo: BloqueoCanchaRequest): Observable<BloqueoCanchaResponse> {
    return this.http.put<BloqueoCanchaResponse>(`${this.API_URL}/bloqueos/${id}`, bloqueo);
  }

  eliminarBloqueo(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/bloqueos/${id}`);
  }
}
