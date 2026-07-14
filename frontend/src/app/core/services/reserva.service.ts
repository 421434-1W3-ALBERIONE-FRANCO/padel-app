import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ReservaRequest, ReservaResponse } from '../../shared/models/reserva.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ReservaService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/reservas`;

  crearReserva(request: ReservaRequest): Observable<ReservaResponse> {
    return this.http.post<ReservaResponse>(this.API_URL, request);
  }

  cancelarReserva(id: number, motivo?: string): Observable<ReservaResponse> {
    let params = new HttpParams();
    if (motivo) {
      params = params.set('motivo', motivo);
    }
    return this.http.put<ReservaResponse>(`${this.API_URL}/${id}/cancelar`, {}, { params });
  }

  getMisReservas(): Observable<ReservaResponse[]> {
    return this.http.get<ReservaResponse[]>(`${this.API_URL}/mis-reservas`);
  }

  getReserva(id: number): Observable<ReservaResponse> {
    return this.http.get<ReservaResponse>(`${this.API_URL}/${id}`);
  }

  getAllReservas(): Observable<ReservaResponse[]> {
    return this.http.get<ReservaResponse[]>(this.API_URL);
  }
}
