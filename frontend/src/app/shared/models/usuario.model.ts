export type RolUsuario = 'ADMIN' | 'RECEPCIONISTA' | 'JUGADOR';

export interface UsuarioResponse {
  id: number;
  nombre: string;
  apellido: string;
  email: string;
  telefono: string;
  rol: RolUsuario;
  activo: boolean;
}

export interface LoginResponse {
  token: string;
  usuario: UsuarioResponse;
}

export interface CrearUsuarioRequest {
  nombre: string;
  apellido: string;
  email: string;
  telefono?: string;
  password: string;
  rol: RolUsuario;
}

export interface LoginRequest {
  email: string;
  password: string;
}
