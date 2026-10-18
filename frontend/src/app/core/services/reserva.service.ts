import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ReservaRequest, ReservaResponse } from '../../shared/models/reserva.model';
import { MetodoMostrador, PagoResponse } from '../../shared/models/pago.model';
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

  /** Staff: todo el historial o, con `fecha` (YYYY-MM-DD), solo las reservas de ese día ordenadas por hora y cancha. */
  getAllReservas(fecha?: string): Observable<ReservaResponse[]> {
    let params = new HttpParams();
    if (fecha) {
      params = params.set('fecha', fecha);
    }
    return this.http.get<ReservaResponse[]>(this.API_URL, { params });
  }

  /**
   * Staff: confirma una reserva PENDIENTE_PAGO sin registrar cobro (el cliente paga al llegar). Queda como
   * pendiente de cobro en la caja del día hasta que se cobra con {@link cobrarEnMostrador}.
   */
  confirmarReserva(id: number): Observable<ReservaResponse> {
    return this.http.put<ReservaResponse>(`${this.API_URL}/${id}/confirmar`, {});
  }

  /** Staff: cobra el turno en el mostrador (efectivo o tarjeta). Si estaba pendiente de pago, lo confirma. */
  cobrarEnMostrador(id: number, metodo: MetodoMostrador): Observable<PagoResponse> {
    return this.http.post<PagoResponse>(`${this.API_URL}/${id}/cobro-mostrador`, { metodo });
  }
}
