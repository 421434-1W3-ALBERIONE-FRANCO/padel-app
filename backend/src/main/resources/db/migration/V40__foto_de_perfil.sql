-- Una única foto de perfil por jugador (la del match de parejas). Los bytes van en su propia tabla para que
-- listar perfiles (el mazo) no los cargue; el perfil guarda cuándo cambió, que el front usa como versión.
ALTER TABLE perfiles_jugador ADD COLUMN foto_actualizada_en TIMESTAMP;

CREATE TABLE fotos_perfil (
    perfil_id BIGINT PRIMARY KEY,
    contenido BYTEA NOT NULL,
    tipo_contenido VARCHAR(30) NOT NULL,
    actualizada_en TIMESTAMP NOT NULL,
    CONSTRAINT fk_fotos_perfil_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles_jugador(id) ON DELETE CASCADE
);
