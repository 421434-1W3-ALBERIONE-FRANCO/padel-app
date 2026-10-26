package com.padel.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Tareas programadas con lock distribuido (ShedLock sobre la tabla {@code shedlock}, V35): si se corre más
 * de una instancia del backend, cada tarea se ejecuta una sola vez en vez de una vez por instancia
 * (recordatorios duplicados, reservas vencidas procesadas en paralelo).
 *
 * <p>Se desactiva con {@code app.scheduling.enabled=false} (los tests no quieren tareas en segundo plano).
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                // La hora la pone la base: no depende de que los relojes de las instancias estén sincronizados.
                .usingDbTime()
                .build());
    }
}
