import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ConfiguracionRequest, ConfiguracionResponse } from '../../shared/models/configuracion.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ConfiguracionService {
  private http = inject(HttpClient);
  private readonly API_URL = environment.apiUrl;

  obtener(): Observable<ConfiguracionResponse> {
    return this.http.get<ConfiguracionResponse>(`${this.API_URL}/configuracion`);
  }

  actualizar(request: ConfiguracionRequest): Observable<ConfiguracionResponse> {
    return this.http.put<ConfiguracionResponse>(`${this.API_URL}/configuracion`, request);
  }
}
