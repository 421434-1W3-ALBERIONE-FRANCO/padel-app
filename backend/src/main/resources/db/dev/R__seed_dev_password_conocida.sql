-- =====================================================================
-- Solo desarrollo (db/dev no se aplica en producción).
-- El hash que cargó V29 no correspondía a "password", la contraseña documentada de los usuarios de prueba:
-- con el seed no se podía entrar. Se corrige acá (V29 ya está aplicado y no se puede editar) y solo para los
-- usuarios que todavía tienen el hash original, así no se pisa una contraseña que alguien haya cambiado.
-- Contraseña de todos los usuarios de prueba: "password"
-- =====================================================================
UPDATE usuarios
   SET password_hash = '$2a$10$pTYpkmWpFpbbj9vZhFqaxOsZF4ilZyfqxNVGIxaPI3bqb6BYqpPrm'
 WHERE password_hash = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';
