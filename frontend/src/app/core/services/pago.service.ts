import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PreferenciaResponse, PagoResponse } from '../../shared/models/pago.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class PagoService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/pagos`;

  crearPreferencia(reservaId: number): Observable<PreferenciaResponse> {
    return this.http.post<PreferenciaResponse>(`${this.API_URL}/preferencias`, { reservaId });
  }

  getPago(id: number): Observable<PagoResponse> {
    return this.http.get<PagoResponse>(`${this.API_URL}/${id}`);
  }
}
