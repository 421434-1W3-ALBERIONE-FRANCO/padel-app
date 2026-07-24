import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CambiarPasswordRequest, RolUsuario, UsuarioResponse } from '../../shared/models/usuario.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class UsuarioService {
  private http = inject(HttpClient);
  private readonly API_URL = environment.apiUrl;

  cambiarPassword(request: CambiarPasswordRequest): Observable<void> {
    return this.http.put<void>(`${this.API_URL}/usuarios/me/password`, request);
  }

  listarPorRol(rol: RolUsuario): Observable<UsuarioResponse[]> {
    const params = new HttpParams().set('rol', rol);
    return this.http.get<UsuarioResponse[]>(`${this.API_URL}/usuarios`, { params });
  }
}
