export type TipoNotificacion =
  | 'RECORDATORIO_RESERVA'
  | 'VENCIMIENTO_BONO'
  | 'RESERVA_CANCELADA_FALTA_PAGO'
  | 'MATCH_NUEVO'
  | 'MATCH_TURNO_RESERVADO';

export interface NotificacionResponse {
  id: number;
  tipo: TipoNotificacion;
  titulo: string;
  mensaje: string;
  leida: boolean;
  referenciaId: number | null;
  createdAt: string;
}
