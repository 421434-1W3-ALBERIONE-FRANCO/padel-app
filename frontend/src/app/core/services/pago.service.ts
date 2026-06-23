import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PreferenciaResponse, PagoResponse } from '../../shared/models/pago.model';

@Injectable({
  providedIn: 'root'
})
export class PagoService {
  private http = inject(HttpClient);
  private readonly API_URL = 'http://localhost:8080/api/v1/pagos';

  crearPreferencia(reservaId: number): Observable<PreferenciaResponse> {
    return this.http.post<PreferenciaResponse>(`${this.API_URL}/preferencias`, { reservaId });
  }

  getPago(id: number): Observable<PagoResponse> {
    return this.http.get<PagoResponse>(`${this.API_URL}/${id}`);
  }
}
