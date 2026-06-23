export interface BloqueoCanchaRequest {
  fechaDesde: string; // YYYY-MM-DD
  fechaHasta: string; // YYYY-MM-DD
  horaDesde: string;  // HH:mm
  horaHasta: string;  // HH:mm
  motivo: string;
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
