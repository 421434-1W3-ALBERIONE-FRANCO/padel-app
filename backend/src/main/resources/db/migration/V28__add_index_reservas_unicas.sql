-- Índice parcial único: garantiza que no haya dos reservas CONFIRMADA/COMPLETADA
-- para la misma cancha, fecha y hora de inicio. Solo posible en PostgreSQL.
-- Comentado en V5 por compatibilidad con H2 durante desarrollo inicial.
CREATE UNIQUE INDEX idx_reservas_unicas_confirmadas
    ON reservas (cancha_id, fecha, hora_inicio)
    WHERE estado_reserva IN ('CONFIRMADA', 'COMPLETADA');
