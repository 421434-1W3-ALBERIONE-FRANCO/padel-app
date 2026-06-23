export interface ProductoRequest {
  nombre: string;
  categoria: string;
  precio: number;
  stock: number;
  activo: boolean;
}

export interface ProductoResponse {
  id: number;
  nombre: string;
  categoria: string;
  precio: number;
  stock: number;
  activo: boolean;
}
