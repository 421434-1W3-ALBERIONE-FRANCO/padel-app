import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { BonoResponse, CrearBonoRequest } from '../../shared/models/bono.model';
import { PagoResponse } from '../../shared/models/pago.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class BonoService {
  private http = inject(HttpClient);
  private readonly API_URL = environment.apiUrl;

  // Signals
  private bonosSignal = signal<BonoResponse[]>([]);
  bonos = this.bonosSignal.asReadonly();

  // Computed signal to sum active hours balance
  saldoHorasBono = computed(() => {
    const todayStr = new Date().toISOString().substring(0, 10);
    return this.bonosSignal()
      .filter(b => b.estado === 'ACTIVO' && b.fechaVencimiento >= todayStr)
      .reduce((total, b) => total + (b.horasTotales - b.horasUsadas), 0);
  });

  cargarMisBonos(): Observable<BonoResponse[]> {
    return this.http.get<BonoResponse[]>(`${this.API_URL}/bonos/mis-bonos`).pipe(
      tap(bonos => this.bonosSignal.set(bonos))
    );
  }

  usarBono(reservaId: number): Observable<PagoResponse> {
    return this.http.post<PagoResponse>(`${this.API_URL}/reservas/${reservaId}/usar-bono`, {}).pipe(
      tap(() => this.cargarMisBonos().subscribe()) // reload bonos to update balance
    );
  }

  asignarBono(request: CrearBonoRequest): Observable<BonoResponse> {
    return this.http.post<BonoResponse>(`${this.API_URL}/bonos`, request);
  }

  getBono(id: number): Observable<BonoResponse> {
    return this.http.get<BonoResponse>(`${this.API_URL}/bonos/${id}`);
  }
}
