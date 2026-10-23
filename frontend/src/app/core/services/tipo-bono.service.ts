import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TipoBonoRequest, TipoBonoResponse } from '../../shared/models/bono.model';
import { environment } from '../../../environments/environment';

/** Catálogo de bonos: lo administra el ADMIN; el staff vende desde acá y los jugadores ven los packs. */
@Injectable({
  providedIn: 'root'
})
export class TipoBonoService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/tipos-bono`;

  /** Los activos, ordenados por horas. `incluirInactivos` solo tiene efecto para el ADMIN. */
  listar(incluirInactivos = false): Observable<TipoBonoResponse[]> {
    const params = new HttpParams().set('incluirInactivos', incluirInactivos);
    return this.http.get<TipoBonoResponse[]>(this.API_URL, { params });
  }

  crear(request: TipoBonoRequest): Observable<TipoBonoResponse> {
    return this.http.post<TipoBonoResponse>(this.API_URL, request);
  }

  actualizar(id: number, request: TipoBonoRequest): Observable<TipoBonoResponse> {
    return this.http.put<TipoBonoResponse>(`${this.API_URL}/${id}`, request);
  }

  /** Baja lógica: deja de ofrecerse; los bonos ya vendidos no cambian. */
  desactivar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }
}
