import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProductoRequest, ProductoResponse } from '../../shared/models/producto.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ProductoService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/productos`;

  crear(request: ProductoRequest): Observable<ProductoResponse> {
    return this.http.post<ProductoResponse>(this.API_URL, request);
  }

  actualizar(id: number, request: ProductoRequest): Observable<ProductoResponse> {
    return this.http.put<ProductoResponse>(`${this.API_URL}/${id}`, request);
  }

  listarActivos(): Observable<ProductoResponse[]> {
    return this.http.get<ProductoResponse[]>(this.API_URL);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }
}
