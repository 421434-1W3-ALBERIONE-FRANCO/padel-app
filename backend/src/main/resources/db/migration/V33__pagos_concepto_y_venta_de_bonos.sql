-- Concepto del pago: el turno, los consumos de la cuenta o la venta de un bono. Hasta ahora el pago del
-- turno y el de los consumos solo se distinguían porque las filas de consumos apuntan a este último.
ALTER TABLE pagos ADD COLUMN concepto VARCHAR(20);
UPDATE pagos SET concepto = 'CONSUMOS' WHERE id IN (SELECT pago_id FROM consumos WHERE pago_id IS NOT NULL);
UPDATE pagos SET concepto = 'TURNO' WHERE concepto IS NULL;
ALTER TABLE pagos ALTER COLUMN concepto SET NOT NULL;

-- La venta de un bono no tiene reserva: el pago apunta al bono vendido.
ALTER TABLE pagos ALTER COLUMN reserva_id DROP NOT NULL;
ALTER TABLE pagos ADD COLUMN bono_id BIGINT;
ALTER TABLE pagos ADD CONSTRAINT fk_pagos_bono FOREIGN KEY (bono_id) REFERENCES bonos(id);
ALTER TABLE pagos ADD CONSTRAINT chk_pagos_reserva_o_bono CHECK (reserva_id IS NOT NULL OR bono_id IS NOT NULL);
CREATE INDEX idx_pagos_bono ON pagos(bono_id);

-- La caja diaria busca los pagos aprobados de un día.
CREATE INDEX idx_pagos_estado_creado ON pagos(estado, created_at);
