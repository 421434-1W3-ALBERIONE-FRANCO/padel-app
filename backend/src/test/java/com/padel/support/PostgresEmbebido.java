package com.padel.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * PostgreSQL real embebido (binarios de zonky, sin Docker) compartido por todos los tests que lo usan.
 * Los tests de integración comunes corren sobre H2; éste existe para validar lo que H2 no puede:
 * las migraciones Flyway reales, sus constraints específicas de Postgres (EXCLUDE, CHECK, índices
 * funcionales) y que el esquema coincida con las entidades (ddl-auto: validate).
 */
public final class PostgresEmbebido {

    private static EmbeddedPostgres instancia;

    private PostgresEmbebido() {
    }

    public static synchronized EmbeddedPostgres obtener() {
        if (instancia == null) {
            try {
                instancia = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo iniciar el PostgreSQL embebido", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    instancia.close();
                } catch (IOException ignored) {
                    // El proceso del test termina igual.
                }
            }));
        }
        return instancia;
    }

    public static String jdbcUrl() {
        return obtener().getJdbcUrl("postgres", "postgres");
    }
}
