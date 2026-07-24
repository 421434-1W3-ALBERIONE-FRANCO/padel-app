import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ReporteComisionesResponse } from '../../shared/models/comision.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ComisionService {
  private http = inject(HttpClient);
  private readonly API_URL = environment.apiUrl;

  obtenerReporte(recepcionistaId: number, desde: string, hasta: string): Observable<ReporteComisionesResponse> {
    const params = new HttpParams()
      .set('recepcionistaId', recepcionistaId)
      .set('desde', desde)
      .set('hasta', hasta);
    return this.http.get<ReporteComisionesResponse>(`${this.API_URL}/comisiones/reporte`, { params });
  }
}
