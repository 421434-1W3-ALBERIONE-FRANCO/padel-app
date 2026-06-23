import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { Client } from '@stomp/stompjs';
import { DisponibilidadResponse } from '../../shared/models/disponibilidad.model';

@Injectable({
  providedIn: 'root'
})
export class DisponibilidadService {
  private http = inject(HttpClient);
  private readonly API_URL = 'http://localhost:8080/api/v1';
  private readonly WS_URL = 'ws://localhost:8080/ws';

  private stompClient: Client | null = null;
  private updatesSubject = new Subject<any>();
  
  // Observable para notificar a los componentes sobre cambios en tiempo real
  updates$ = this.updatesSubject.asObservable();

  constructor() {
    this.initWebSocket();
  }

  getDisponibilidad(canchaId: number, fecha: string): Observable<DisponibilidadResponse> {
    return this.http.get<DisponibilidadResponse>(`${this.API_URL}/disponibilidad`, {
      params: { fecha, cancha: canchaId.toString() }
    });
  }

  private initWebSocket() {
    try {
      this.stompClient = new Client({
        brokerURL: this.WS_URL,
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        debug: (str) => {
          console.log('STOMP DEBUG:', str);
        }
      });

      this.stompClient.onConnect = () => {
        console.log('WebSocket connected successfully');
        this.stompClient?.subscribe('/topic/disponibilidad', (message) => {
          try {
            const payload = JSON.parse(message.body);
            this.updatesSubject.next(payload);
          } catch (e) {
            console.error('Error parsing WS message payload', e);
          }
        });
      };

      this.stompClient.onStompError = (frame) => {
        console.error('Broker reported error: ' + frame.headers['message']);
        console.error('Additional details: ' + frame.body);
      };

      this.stompClient.activate();
    } catch (err) {
      console.error('Failed to initialize WebSocket client:', err);
    }
  }
}
