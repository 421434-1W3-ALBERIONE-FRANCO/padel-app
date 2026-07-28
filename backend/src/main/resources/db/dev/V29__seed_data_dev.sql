-- =====================================================================
-- SEED DATA para desarrollo/testing local
-- Contraseña para TODOS los usuarios de prueba: "password"
-- Hash BCrypt(10): $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- =====================================================================

-- Usuarios de prueba (password = "password" para todos)
INSERT INTO usuarios (nombre, apellido, email, telefono, password_hash, rol, activo, created_at)
VALUES
    ('Admin',    'PadelApp',  'admin@padelapp.com',      '+5491100000001', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN',         TRUE, NOW()),
    ('Sofia',    'Recepcion', 'recepcion@padelapp.com',  '+5491100000002', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'RECEPCIONISTA', TRUE, NOW()),
    ('Carlos',   'Jugador',   'cliente@padelapp.com',    '+5491100000003', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'JUGADOR',       TRUE, NOW()),
    ('Laura',    'Jugadora',  'cliente2@padelapp.com',   '+5491100000004', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'JUGADOR',       TRUE, NOW());

-- Canchas
INSERT INTO canchas (nombre, tipo_suelo, techada, tiene_luz, activa, created_at)
VALUES
    ('Cancha 1 - Césped',  'CESPED',  FALSE, TRUE,  TRUE, NOW()),
    ('Cancha 2 - Cristal', 'CRISTAL', TRUE,  TRUE,  TRUE, NOW()),
    ('Cancha 3 - Cemento', 'CEMENTO', TRUE,  FALSE, TRUE, NOW());

-- Franjas horarias para TODAS las canchas, todos los días de la semana
INSERT INTO franjas_horarias (cancha_id, hora_inicio, hora_fin, duracion_min, precio_base, precio_nocturno, dias_aplicables, created_at)
SELECT c.id,
       s.hora_inicio::TIME,
       s.hora_fin::TIME,
       90,
       s.precio_base,
       s.precio_noc,
       'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY',
       NOW()
FROM canchas c
CROSS JOIN (VALUES
    ('08:00', '09:30', 3500.00, 3500.00),
    ('09:30', '11:00', 3500.00, 3500.00),
    ('11:00', '12:30', 3500.00, 3500.00),
    ('12:30', '14:00', 3500.00, 3500.00),
    ('14:00', '15:30', 4000.00, 4000.00),
    ('15:30', '17:00', 4000.00, 4000.00),
    ('17:00', '18:30', 4000.00, 5500.00),
    ('18:30', '20:00', 4500.00, 5500.00),
    ('20:00', '21:30', 4500.00, 5500.00),
    ('21:30', '23:00', 4500.00, 5500.00)
) AS s(hora_inicio, hora_fin, precio_base, precio_noc)
WHERE c.activa = TRUE;

-- Productos de tienda
INSERT INTO productos (nombre, categoria, precio, stock, activo, created_at)
VALUES
    ('Pelota de Pádel Head',  'PELOTA',    2800.00, 50, TRUE, NOW()),
    ('Gatorade 500ml',        'BEBIDA',     900.00, 80, TRUE, NOW()),
    ('Agua Mineral 600ml',    'BEBIDA',     500.00,120, TRUE, NOW()),
    ('Grip Overgrip x3',      'ACCESORIO', 1500.00, 30, TRUE, NOW()),
    ('Muñequera Head',        'ACCESORIO', 2200.00, 20, TRUE, NOW()),
    ('Barra de Proteína',     'ALIMENTO',  1200.00, 40, TRUE, NOW());
