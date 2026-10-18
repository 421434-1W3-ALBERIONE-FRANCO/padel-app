import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CajaDiariaResponse } from '../../shared/models/caja.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class CajaService {
  private http = inject(HttpClient);
  private readonly API_URL = environment.apiUrl;

  /** Staff: lo cobrado en el día (por método y movimiento) y los turnos del día que faltan cobrar. */
  obtenerCajaDiaria(fecha: string): Observable<CajaDiariaResponse> {
    return this.http.get<CajaDiariaResponse>(`${this.API_URL}/reportes/caja-diaria`, { params: { fecha } });
  }
}
