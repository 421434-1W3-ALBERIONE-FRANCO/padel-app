package com.padel.scheduler;

import com.padel.service.BonoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BonoVencimientoScheduler {

    private final BonoService bonoService;

    // Ejecutar todos los días a la 1:00 AM
    @Scheduled(cron = "0 0 1 * * ?")
    @SchedulerLock(name = "vencimientoBonos", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void expirarBonosVencidos() {
        log.info("Iniciando tarea programada de vencimiento de bonos");
        try {
            bonoService.expirarBonosVencidos();
        } catch (Exception e) {
            log.error("Error durante la expiración de bonos: {}", e.getMessage(), e);
        }
    }
}
