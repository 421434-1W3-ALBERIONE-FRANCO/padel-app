export interface OpcionDuracion {
  duracionMin: number;
  horaFin: string;
  precio: number;
}

export interface SlotDisponibilidad {
  franjaId: number;
  horaInicio: string;
  horaFin: string;
  duracionMin: number;
  precio: number;
  disponible: boolean;
  motivoBloqueo: string | null;
  opcionesDuracion: OpcionDuracion[];
}

export interface DisponibilidadResponse {
  canchaId: number;
  nombreCancha: string;
  fecha: string;
  slots: SlotDisponibilidad[];
}
