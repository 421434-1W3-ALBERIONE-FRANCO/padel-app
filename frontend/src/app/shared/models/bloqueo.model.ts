export interface BloqueoCanchaRequest {
  fechaDesde: string; // YYYY-MM-DD
  fechaHasta: string; // YYYY-MM-DD
  horaDesde: string;  // HH:mm
  horaHasta: string;  // HH:mm
  motivo: string;
  /** Si el bloqueo pisa reservas vigentes hay que confirmar que se cancelen (con reintegro y aviso). */
  cancelarReservasAfectadas?: boolean;
}

export interface BloqueoCanchaResponse {
  id: number;
  canchaId: number;
  fechaDesde: string;
  fechaHasta: string;
  horaDesde: string;
  horaHasta: string;
  motivo: string;
  createdAt: string;
  updatedAt: string;
}
