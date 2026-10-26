export type TipoNotificacion =
  | 'RECORDATORIO_RESERVA'
  | 'VENCIMIENTO_BONO'
  | 'RESERVA_CANCELADA_FALTA_PAGO'
  | 'RESERVA_CANCELADA'
  | 'MATCH_NUEVO'
  | 'MATCH_TURNO_RESERVADO'
  | 'REINTEGRO'
  | 'REINTEGRO_PENDIENTE'
  | 'PAGO_EN_REVISION'
  | 'INVITACION_TORNEO'
  | 'INVITACION_TORNEO_RESPONDIDA';

export interface NotificacionResponse {
  id: number;
  tipo: TipoNotificacion;
  titulo: string;
  mensaje: string;
  leida: boolean;
  referenciaId: number | null;
  createdAt: string;
}
