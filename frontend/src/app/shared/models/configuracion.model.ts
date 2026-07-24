export interface ConfiguracionResponse {
  comisionesHabilitadas: boolean;
  porcentajeComisionRecepcion: number;
  diasAvisoVencimientoBono: number;
  horaTopeCierreSesion: string;
  updatedAt: string;
}

export interface ConfiguracionRequest {
  comisionesHabilitadas: boolean;
  porcentajeComisionRecepcion: number;
  diasAvisoVencimientoBono: number;
  horaTopeCierreSesion: string;
}
