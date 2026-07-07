import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { CrearUsuarioRequest, LoginRequest, LoginResponse, UsuarioResponse } from '../../shared/models/usuario.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private readonly API_URL = environment.apiUrl;
  private readonly TOKEN_KEY = 'padel_token';
  private readonly USER_KEY = 'padel_user';

  // Signals para el manejo del estado
  private currentUserSignal = signal<UsuarioResponse | null>(null);
  
  // Exponer signals de lectura
  currentUser = this.currentUserSignal.asReadonly();
  isAuthenticated = computed(() => this.currentUserSignal() !== null);
  userRole = computed(() => this.currentUserSignal()?.rol ?? null);

  constructor() {
    this.restoreSession();
  }

  registrar(request: CrearUsuarioRequest): Observable<UsuarioResponse> {
    return this.http.post<UsuarioResponse>(`${this.API_URL}/auth/registro`, request);
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.API_URL}/auth/login`, request).pipe(
      tap(response => {
        this.saveSession(response.token, response.usuario);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSignal.set(null);
    this.router.navigate(['/auth/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  private saveSession(token: string, usuario: UsuarioResponse): void {
    localStorage.setItem(this.TOKEN_KEY, token);
    localStorage.setItem(this.USER_KEY, JSON.stringify(usuario));
    this.currentUserSignal.set(usuario);
  }

  private restoreSession(): void {
    const token = this.getToken();
    const userJson = localStorage.getItem(this.USER_KEY);

    if (token && userJson) {
      if (this.isTokenExpired(token)) {
        this.logout();
        return;
      }
      try {
        const usuario: UsuarioResponse = JSON.parse(userJson);
        this.currentUserSignal.set(usuario);
      } catch (e) {
        this.logout();
      }
    }
  }

  /**
   * Decodifica el claim `exp` del JWT (sin validar firma, eso lo hace el backend) para
   * evitar restaurar en el front una sesión cuyo token ya venció. El token mock de
   * desarrollo no es un JWT real (no tiene 3 segmentos), así que se lo asume no vencido.
   */
  private isTokenExpired(token: string): boolean {
    const segmentos = token.split('.');
    if (segmentos.length !== 3) return false;
    try {
      const payload = JSON.parse(atob(segmentos[1].replace(/-/g, '+').replace(/_/g, '/')));
      if (typeof payload.exp !== 'number') return false;
      return Date.now() >= payload.exp * 1000;
    } catch {
      return false;
    }
  }
}
