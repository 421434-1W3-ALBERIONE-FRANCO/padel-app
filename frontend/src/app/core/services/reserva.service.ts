import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EstadoReserva, ReservaRequest, ReservaResponse } from '../../shared/models/reserva.model';
import { PaginaResponse } from '../../shared/models/pagina.model';
import { MetodoMostrador, PagoResponse } from '../../shared/models/pago.model';
import { environment } from '../../../environments/environment';

export interface FiltrosReservas {
  desde?: string;
  hasta?: string;
  estado?: EstadoReserva;
  q?: string;
  page?: number;
  size?: number;
}

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

  /** Staff: búsqueda paginada en el backend (más recientes primero). `q` busca por cliente o cancha. */
  buscarReservas(filtros: FiltrosReservas = {}): Observable<PaginaResponse<ReservaResponse>> {
    let params = new HttpParams()
      .set('page', filtros.page ?? 0)
      .set('size', filtros.size ?? 20);
    if (filtros.desde) params = params.set('desde', filtros.desde);
    if (filtros.hasta) params = params.set('hasta', filtros.hasta);
    if (filtros.estado) params = params.set('estado', filtros.estado);
    if (filtros.q?.trim()) params = params.set('q', filtros.q.trim());
    return this.http.get<PaginaResponse<ReservaResponse>>(this.API_URL, { params });
  }

  /** Staff: las reservas de un día (YYYY-MM-DD) en todos sus estados, ordenadas por hora y cancha. */
  getAgenda(fecha: string): Observable<ReservaResponse[]> {
    return this.http.get<ReservaResponse[]>(`${this.API_URL}/agenda`, { params: { fecha } });
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
