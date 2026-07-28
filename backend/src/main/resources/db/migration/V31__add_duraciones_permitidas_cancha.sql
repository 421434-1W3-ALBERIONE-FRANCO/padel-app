-- Reservas de duración variable: cada cancha define qué duraciones (en minutos)
-- se pueden reservar, separadas por coma. Default '90' (turno estándar de pádel).
ALTER TABLE canchas ADD COLUMN duraciones_permitidas VARCHAR(100) DEFAULT '90';
UPDATE canchas SET duraciones_permitidas = '90' WHERE duraciones_permitidas IS NULL;
