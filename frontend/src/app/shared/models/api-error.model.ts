/** Formato único de los errores del backend. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  /** Solo en los errores que el frontend necesita distinguir. */
  codigo?: CodigoError;
}

export type CodigoError = 'EMAIL_NO_VERIFICADO' | 'RESERVAS_AFECTADAS' | 'DEMASIADOS_INTENTOS';
