import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ConsumoRequest, ConsumoResponse, CerrarCuentaRequest, CajaDiariaResponse } from '../../shared/models/consumo.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ConsumoService {
  private http = inject(HttpClient);
  private readonly BASE_URL = environment.apiUrl;

  cargarConsumo(reservaId: number, request: ConsumoRequest): Observable<ConsumoResponse> {
    return this.http.post<ConsumoResponse>(`${this.BASE_URL}/reservas/${reservaId}/consumos`, request);
  }

  obtenerConsumos(reservaId: number): Observable<ConsumoResponse[]> {
    return this.http.get<ConsumoResponse[]>(`${this.BASE_URL}/reservas/${reservaId}/consumos`);
  }

  cerrarCuenta(reservaId: number, request: CerrarCuentaRequest): Observable<void> {
    return this.http.put<void>(`${this.BASE_URL}/reservas/${reservaId}/cerrar-cuenta`, request);
  }

  obtenerCajaDiaria(fecha: string): Observable<CajaDiariaResponse> {
    return this.http.get<CajaDiariaResponse>(`${this.BASE_URL}/reportes/caja-diaria`, {
      params: { fecha }
    });
  }
}
