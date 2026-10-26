-- =====================================================================================================
-- Integridad de reservas, bloqueo optimista y reintegros (auditoría 2026-10).
-- =====================================================================================================

-- 1) Reservas vigentes cuyo horario pasaba la medianoche (fin <= inicio): rompían los controles de
--    superposición y el precio. Con el nuevo control ya no se pueden crear; las existentes se cancelan.
UPDATE reservas
   SET estado_reserva = 'CANCELADA',
       motivo_cancelacion = 'Cancelada al migrar: el horario pasaba la medianoche (V34)'
 WHERE hora_fin <= hora_inicio
   AND estado_reserva IN ('PENDIENTE_PAGO', 'CONFIRMADA');

-- 2) Reservas vigentes superpuestas en la misma cancha (posibles por la condición de carrera que se
--    corrige): se conserva la más antigua y se cancela la otra, para poder crear la restricción del paso 4.
UPDATE reservas r
   SET estado_reserva = 'CANCELADA',
       motivo_cancelacion = 'Cancelada al migrar: se superponía con otra reserva de la misma cancha (V34)'
 WHERE r.estado_reserva IN ('PENDIENTE_PAGO', 'CONFIRMADA')
   AND EXISTS (
       SELECT 1
         FROM reservas o
        WHERE o.id < r.id
          AND o.cancha_id = r.cancha_id
          AND o.fecha = r.fecha
          AND o.estado_reserva IN ('PENDIENTE_PAGO', 'CONFIRMADA')
          AND o.hora_inicio < r.hora_fin
          AND r.hora_inicio < o.hora_fin);

-- 3) Invariantes de los rangos horarios. NOT VALID: no se revalidan las filas históricas (canceladas), pero
--    sí cualquier fila nueva o modificada.
ALTER TABLE reservas
    ADD CONSTRAINT chk_reservas_rango_horario CHECK (hora_fin > hora_inicio) NOT VALID;
ALTER TABLE franjas_horarias
    ADD CONSTRAINT chk_franjas_rango_horario CHECK (hora_fin > hora_inicio) NOT VALID;
ALTER TABLE bloqueos_canchas
    ADD CONSTRAINT chk_bloqueos_rango CHECK (hora_hasta > hora_desde AND fecha_hasta >= fecha_desde) NOT VALID;

-- 4) Garantía final contra reservas superpuestas: dos reservas vigentes de la misma cancha no pueden compartir
--    ni un minuto. El lock de Redis solo cubría la hora de inicio y el índice único de V28 solo el mismo
--    (cancha, fecha, hora_inicio): dos pedidos simultáneos 18:00x120 y 19:00x60 se guardaban los dos.
--    btree_gist es una extensión "trusted" (PG13+): no requiere superusuario.
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE reservas
    ADD CONSTRAINT excl_reservas_superpuestas
    EXCLUDE USING gist (
        cancha_id WITH =,
        tsrange(fecha + hora_inicio, fecha + hora_fin, '[)') WITH &&
    )
    WHERE (estado_reserva IN ('PENDIENTE_PAGO', 'CONFIRMADA'));

-- 5) Bloqueo optimista (@Version): dos operaciones simultáneas sobre el mismo registro ya no se pisan.
ALTER TABLE reservas ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE pagos ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bonos ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE productos ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE consumos ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE banco_horas ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ranking_jugadores ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE torneos ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 6) Reintegros: un pago de concepto REINTEGRO (monto negativo) apunta al cobro que devuelve.
ALTER TABLE pagos ADD COLUMN pago_original_id BIGINT;
ALTER TABLE pagos ADD CONSTRAINT fk_pagos_pago_original FOREIGN KEY (pago_original_id) REFERENCES pagos(id);
ALTER TABLE pagos ADD COLUMN intentos_reintegro INT NOT NULL DEFAULT 0;
-- Momento en que la plata entró o salió de la caja (la caja diaria agrupa por esta fecha).
ALTER TABLE pagos ADD COLUMN aprobado_en TIMESTAMP;
UPDATE pagos SET aprobado_en = created_at WHERE estado = 'APROBADO';
CREATE INDEX idx_pagos_estado_aprobado ON pagos(estado, aprobado_en);
CREATE INDEX idx_pagos_pago_original ON pagos(pago_original_id);

-- 7) Consultas frecuentes: reservas pendientes por jugador (límite anti-acaparamiento) y vigentes por cancha.
CREATE INDEX idx_reservas_usuario_estado ON reservas(usuario_id, estado_reserva);
CREATE INDEX idx_reservas_cancha_fecha ON reservas(cancha_id, fecha);
