export type TipoSuelo = 'BLINDEX' | 'CEMENTO' | 'SINTETICO';

export interface CanchaRequest {
  nombre: string;
  tipoSuelo: TipoSuelo;
  techada: boolean;
  tieneLuz: boolean;
}

export interface CanchaResponse {
  id: number;
  nombre: string;
  tipoSuelo: TipoSuelo;
  techada: boolean;
  tieneLuz: boolean;
  activa: boolean;
}
