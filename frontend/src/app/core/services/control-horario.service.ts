import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  BancoHorasResponse,
  LiquidacionRequest,
  LiquidacionResponse,
  TurnoRequest,
  TurnoResponse
} from '../../shared/models/control-horario.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ControlHorarioService {
  private http = inject(HttpClient);
  private readonly API_URL = environment.apiUrl;

  // Turnos
  listarTurnos(empleadoId: number): Observable<TurnoResponse[]> {
    const params = new HttpParams().set('empleadoId', empleadoId);
    return this.http.get<TurnoResponse[]>(`${this.API_URL}/turnos`, { params });
  }

  crearTurno(request: TurnoRequest): Observable<TurnoResponse> {
    return this.http.post<TurnoResponse>(`${this.API_URL}/turnos`, request);
  }

  eliminarTurno(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/turnos/${id}`);
  }

  // Liquidaciones
  liquidar(request: LiquidacionRequest): Observable<LiquidacionResponse> {
    return this.http.post<LiquidacionResponse>(`${this.API_URL}/liquidaciones`, request);
  }

  historialLiquidaciones(empleadoId: number): Observable<LiquidacionResponse[]> {
    const params = new HttpParams().set('empleadoId', empleadoId);
    return this.http.get<LiquidacionResponse[]>(`${this.API_URL}/liquidaciones`, { params });
  }

  bancoHoras(empleadoId: number): Observable<BancoHorasResponse> {
    const params = new HttpParams().set('empleadoId', empleadoId);
    return this.http.get<BancoHorasResponse>(`${this.API_URL}/liquidaciones/banco-horas`, { params });
  }
}
