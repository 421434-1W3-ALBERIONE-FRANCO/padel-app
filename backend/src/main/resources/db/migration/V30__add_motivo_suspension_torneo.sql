-- Fase 2: soporte de suspensión de torneos con motivo.
-- El estado SUSPENDIDO se agrega a nivel aplicación (enum EstadoTorneo);
-- acá solo persistimos el motivo de la suspensión.
ALTER TABLE torneos ADD COLUMN motivo_suspension VARCHAR(500);
