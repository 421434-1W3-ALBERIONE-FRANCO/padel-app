export type DiaSemana = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface TurnoRequest {
  empleadoEmail: string;
  diaSemana: DiaSemana;
  horaInicio: string;
  horaFin: string;
}

export interface TurnoResponse {
  id: number;
  empleadoId: number;
  empleadoEmail: string;
  diaSemana: DiaSemana;
  horaInicio: string;
  horaFin: string;
}

export interface LiquidacionRequest {
  empleadoId: number;
  desde: string;
  hasta: string;
}

export interface LiquidacionResponse {
  id: number;
  empleadoId: number;
  empleadoEmail: string;
  periodoDesde: string;
  periodoHasta: string;
  totalMinutosTrabajados: number;
  totalMinutosBancoHoras: number;
  totalComisiones: number;
  generadaEn: string;
}

export interface BancoHorasResponse {
  empleadoId: number;
  empleadoEmail: string;
  saldoMinutos: number;
}
