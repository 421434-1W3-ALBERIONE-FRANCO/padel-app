ALTER TABLE pagos ADD COLUMN atendido_por_id BIGINT;
ALTER TABLE pagos ADD CONSTRAINT fk_pagos_atendido_por FOREIGN KEY (atendido_por_id) REFERENCES usuarios(id);

ALTER TABLE bonos ADD COLUMN vendido_por_id BIGINT;
ALTER TABLE bonos ADD CONSTRAINT fk_bonos_vendido_por FOREIGN KEY (vendido_por_id) REFERENCES usuarios(id);
