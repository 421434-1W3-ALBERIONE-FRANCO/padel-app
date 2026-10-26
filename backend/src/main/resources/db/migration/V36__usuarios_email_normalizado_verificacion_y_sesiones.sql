-- =====================================================================================================
-- Emails normalizados, verificación de email y versión de credenciales (auditoría 2026-10).
-- =====================================================================================================

-- 1) Emails en minúsculas y únicos sin distinguir mayúsculas. Antes "JUAN@x.com" y "juan@x.com" podían ser
--    dos cuentas distintas, y una validación de dueño que comparaba sin mayúsculas dejaba operar sobre
--    recursos ajenos. Si hay cuentas que solo difieren en mayúsculas no se toma una decisión automática.
DO $$
BEGIN
    IF EXISTS (SELECT lower(trim(email)) FROM usuarios GROUP BY lower(trim(email)) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Hay cuentas cuyo email solo difiere en mayúsculas o espacios. Unificarlas a mano antes de aplicar V36: SELECT lower(trim(email)), count(*) FROM usuarios GROUP BY 1 HAVING count(*) > 1;';
    END IF;
END $$;

UPDATE usuarios SET email = lower(trim(email)) WHERE email <> lower(trim(email));
CREATE UNIQUE INDEX ux_usuarios_email_lower ON usuarios (lower(email));
-- Redundante: la restricción UNIQUE de la columna ya crea su propio índice.
DROP INDEX IF EXISTS idx_usuarios_email;

-- 2) Verificación de email: las cuentas existentes se consideran verificadas; las nuevas del registro
--    público confirman el email antes de poder iniciar sesión.
ALTER TABLE usuarios ADD COLUMN email_verificado BOOLEAN NOT NULL DEFAULT TRUE;

-- 3) Versión de credenciales: viaja en el JWT y cambiar o resetear la contraseña la incrementa, con lo que
--    las sesiones abiertas (y un token robado) dejan de servir.
ALTER TABLE usuarios ADD COLUMN token_version INT NOT NULL DEFAULT 0;

-- 4) La tabla de tokens de recuperación pasa a guardar también los de verificación de email.
ALTER TABLE tokens_recuperacion_password ADD COLUMN tipo VARCHAR(30) NOT NULL DEFAULT 'RECUPERACION_PASSWORD';
CREATE INDEX idx_tokens_usuario_tipo ON tokens_recuperacion_password (usuario_id, tipo, usado);
