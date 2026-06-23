export interface FranjaHorariaRequest {
  horaInicio: string; // HH:mm
  horaFin: string;    // HH:mm
  precioBase: number;
  precioNocturno: number;
  diasAplicables: string[]; // ['MONDAY', 'TUESDAY', ...]
}

export interface FranjaHorariaResponse {
  id: number;
  canchaId: number;
  horaInicio: string;
  horaFin: string;
  duracionMin: number;
  precioBase: number;
  precioNocturno: number;
  diasAplicables: string[];
  createdAt: string;
  updatedAt: string;
}
