export type RolUsuario = 'ADMIN' | 'RECEPCIONISTA' | 'JUGADOR';

export interface UsuarioResponse {
  id: number;
  nombre: string;
  apellido: string;
  email: string;
  telefono: string | null;
  rol: RolUsuario;
  activo: boolean;
  /** false mientras el jugador no confirma el enlace que recibió por email al registrarse. */
  emailVerificado: boolean;
}

export interface LoginResponse {
  token: string;
  usuario: UsuarioResponse;
}

/** Registro público: siempre crea un JUGADOR (el rol no forma parte del pedido). */
export interface CrearUsuarioRequest {
  nombre: string;
  apellido: string;
  email: string;
  telefono?: string;
  password: string;
}

export interface CrearStaffRequest {
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

export interface CambiarPasswordRequest {
  passwordActual: string;
  passwordNueva: string;
}

export interface SolicitarRecuperacionRequest {
  email: string;
}

export interface ResetearPasswordRequest {
  token: string;
  passwordNueva: string;
}

export interface VerificarEmailRequest {
  token: string;
}

export interface ReenviarVerificacionRequest {
  email: string;
}
