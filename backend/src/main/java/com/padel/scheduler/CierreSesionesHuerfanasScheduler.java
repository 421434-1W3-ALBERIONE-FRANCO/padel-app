package com.padel.scheduler;

import com.padel.service.SesionTrabajoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CierreSesionesHuerfanasScheduler {

    private final SesionTrabajoService sesionTrabajoService;

    // Ejecutar todos los días a las 23:55 para cerrar sesiones que quedaron abiertas
    // (empleado que cerró el navegador sin hacer logout explícito).
    @Scheduled(cron = "0 55 23 * * ?")
    @SchedulerLock(name = "cierreSesionesHuerfanas", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void cerrarSesionesHuerfanas() {
        log.info("Iniciando tarea programada de cierre de sesiones de trabajo huérfanas");
        try {
            sesionTrabajoService.cerrarSesionesHuerfanas();
        } catch (Exception e) {
            log.error("Error durante el cierre de sesiones huérfanas: {}", e.getMessage(), e);
        }
    }
}
