import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CajaDiariaResponse } from '../../shared/models/caja.model';
import { PagoResponse } from '../../shared/models/pago.model';
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

  /**
   * Staff: devoluciones por entregar (montos negativos). Las de turnos pagados en el club se devuelven en el
   * mostrador; las de MercadoPago aparecen acá solo si el reintegro automático no se pudo hacer.
   */
  reintegrosPendientes(): Observable<PagoResponse[]> {
    return this.http.get<PagoResponse[]>(`${this.API_URL}/reintegros/pendientes`);
  }

  /** Staff: registra que el reintegro se entregó; queda en la caja del día como movimiento negativo. */
  registrarEntregaReintegro(id: number): Observable<PagoResponse> {
    return this.http.put<PagoResponse>(`${this.API_URL}/reintegros/${id}/entrega`, {});
  }
}
