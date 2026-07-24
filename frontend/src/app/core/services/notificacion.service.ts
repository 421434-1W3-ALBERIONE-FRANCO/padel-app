import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { NotificacionResponse } from '../../shared/models/notificacion.model';
import { environment } from '../../../environments/environment';

const POLLING_MS = 60000;

@Injectable({
  providedIn: 'root'
})
export class NotificacionService {
  private http = inject(HttpClient);
  private readonly API_URL = environment.apiUrl;

  private notificacionesSignal = signal<NotificacionResponse[]>([]);
  private contadorNoLeidasSignal = signal<number>(0);

  notificaciones = this.notificacionesSignal.asReadonly();
  contadorNoLeidas = this.contadorNoLeidasSignal.asReadonly();

  private pollingId: ReturnType<typeof setInterval> | null = null;

  cargarMias(): Observable<NotificacionResponse[]> {
    return this.http.get<NotificacionResponse[]>(`${this.API_URL}/notificaciones`).pipe(
      tap(data => this.notificacionesSignal.set(data))
    );
  }

  actualizarContador(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.API_URL}/notificaciones/no-leidas/contador`).pipe(
      tap(res => this.contadorNoLeidasSignal.set(res.count))
    );
  }

  marcarLeida(id: number): Observable<void> {
    return this.http.patch<void>(`${this.API_URL}/notificaciones/${id}/leida`, {}).pipe(
      tap(() => {
        this.notificacionesSignal.update(lista => lista.map(n => n.id === id ? { ...n, leida: true } : n));
        this.actualizarContador().subscribe();
      })
    );
  }

  marcarTodasLeidas(): Observable<void> {
    return this.http.patch<void>(`${this.API_URL}/notificaciones/leer-todas`, {}).pipe(
      tap(() => {
        this.notificacionesSignal.update(lista => lista.map(n => ({ ...n, leida: true })));
        this.contadorNoLeidasSignal.set(0);
      })
    );
  }

  iniciarPolling(): void {
    if (this.pollingId) return;
    this.actualizarContador().subscribe({ error: () => {} });
    this.pollingId = setInterval(() => {
      this.actualizarContador().subscribe({ error: () => {} });
    }, POLLING_MS);
  }

  detenerPolling(): void {
    if (this.pollingId) {
      clearInterval(this.pollingId);
      this.pollingId = null;
    }
  }
}
