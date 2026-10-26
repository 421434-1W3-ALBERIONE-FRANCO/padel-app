-- Lock distribuido de las tareas programadas (ShedLock): con varias instancias del backend cada tarea
-- corre una sola vez.
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
