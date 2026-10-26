import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MetricasReservasResponse } from '../../shared/models/caja.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ReporteService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/reportes`;

  /** Admin: métricas de reservas entre dos fechas (YYYY-MM-DD, hasta 92 días), calculadas en el backend. */
  metricasReservas(desde: string, hasta: string): Observable<MetricasReservasResponse> {
    return this.http.get<MetricasReservasResponse>(`${this.API_URL}/metricas-reservas`, { params: { desde, hasta } });
  }
}
