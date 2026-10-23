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
public class AvisoVencimientoBonoScheduler {

    private final BonoService bonoService;

    // Ejecutar todos los días a las 8:30 AM (después del vencimiento de bonos a la 1 AM)
    @Scheduled(cron = "0 30 8 * * ?")
    @SchedulerLock(name = "avisoVencimientoBonos", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void enviarAvisosVencimiento() {
        log.info("Iniciando tarea programada de aviso de vencimiento próximo de bonos");
        try {
            bonoService.notificarVencimientosProximos();
        } catch (Exception e) {
            log.error("Error durante el envío de avisos de vencimiento de bono: {}", e.getMessage(), e);
        }
    }
}
