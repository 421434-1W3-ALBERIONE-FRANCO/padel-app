package com.padel.scheduler;

import com.padel.service.ReintegroService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Devuelve por MercadoPago los reintegros encolados al cancelar turnos. Corre fuera de la transacción de la
 * cancelación: si MercadoPago no responde, la cancelación ya quedó guardada y el reintegro se reintenta acá.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReintegrosAutomaticosScheduler {

    private final ReintegroService reintegroService;

    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    @SchedulerLock(name = "reintegrosAutomaticos", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void procesar() {
        try {
            reintegroService.procesarReintegrosAutomaticos();
        } catch (Exception e) {
            log.error("Error procesando reintegros automáticos: {}", e.getMessage(), e);
        }
    }
}
