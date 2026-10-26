import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { CrearUsuarioRequest, LoginRequest, LoginResponse, ResetearPasswordRequest, SolicitarRecuperacionRequest, UsuarioResponse } from '../../shared/models/usuario.model';
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

  /** Con la verificación de email activa, la cuenta queda pendiente (emailVerificado = false) hasta confirmar el enlace. */
  registrar(request: CrearUsuarioRequest): Observable<UsuarioResponse> {
    return this.http.post<UsuarioResponse>(`${this.API_URL}/auth/registro`, request);
  }

  verificarEmail(token: string): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/auth/verificacion-email`, { token });
  }

  /** El backend responde igual exista o no la cuenta, para no revelar qué emails están registrados. */
  reenviarVerificacion(email: string): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/auth/verificacion-email/reenvio`, { email });
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.API_URL}/auth/login`, request).pipe(
      tap(response => {
        this.saveSession(response.token, response.usuario);
      })
    );
  }

  solicitarRecuperacionPassword(request: SolicitarRecuperacionRequest): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/auth/recuperar-password`, request);
  }

  resetearPassword(request: ResetearPasswordRequest): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/auth/resetear-password`, request);
  }

  /**
   * @param notificarServidor false cuando el token ya no es válido (401): el backend no podría
   *        identificar al usuario y el POST /auth/logout solo fallaría.
   */
  logout(notificarServidor = true): void {
    // Revoca el token en el backend: deja de servir aunque todavía no haya vencido (fire-and-forget: un error
    // de red no debe impedir el cierre local). Cubre tanto el botón manual como el timeout de inactividad.
    if (notificarServidor && this.getToken()) {
      this.http.post<void>(`${this.API_URL}/auth/logout`, {}).subscribe({ next: () => {}, error: () => {} });
    }
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
        this.logout(false);
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
   * evitar restaurar en el front una sesión cuyo token ya venció. Un token ilegible cuenta como vencido.
   */
  private isTokenExpired(token: string): boolean {
    const segmentos = token.split('.');
    if (segmentos.length !== 3) return true;
    try {
      const payload = JSON.parse(atob(segmentos[1].replace(/-/g, '+').replace(/_/g, '/')));
      if (typeof payload.exp !== 'number') return true;
      return Date.now() >= payload.exp * 1000;
    } catch {
      return true;
    }
  }
}
