CREATE TABLE configuracion_sistema (
    id BIGINT PRIMARY KEY,
    comisiones_habilitadas BOOLEAN NOT NULL DEFAULT FALSE,
    porcentaje_comision_recepcion DECIMAL(5,2) NOT NULL DEFAULT 10.00,
    dias_aviso_vencimiento_bono INT NOT NULL DEFAULT 7,
    hora_tope_cierre_sesion TIME NOT NULL DEFAULT '23:59:00',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

INSERT INTO configuracion_sistema (id, comisiones_habilitadas, porcentaje_comision_recepcion, dias_aviso_vencimiento_bono, hora_tope_cierre_sesion, created_at, updated_at)
VALUES (1, FALSE, 10.00, 7, '23:59:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
