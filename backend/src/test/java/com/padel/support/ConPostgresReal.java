package com.padel.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base de los tests que necesitan PostgreSQL real: levanta la aplicación completa sobre el Postgres
 * embebido, con Flyway aplicando todas las migraciones y Hibernate validando el esquema.
 */
@SpringBootTest
@ActiveProfiles({"test", "pgtest"})
public abstract class ConPostgresReal {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresEmbebido::jdbcUrl);
    }
}
