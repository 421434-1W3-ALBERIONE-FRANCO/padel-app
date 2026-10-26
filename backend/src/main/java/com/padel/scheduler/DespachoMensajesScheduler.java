package com.padel.scheduler;

import com.padel.service.impl.DespachadorMensajes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Envía la bandeja de salida (emails y WhatsApp) cada pocos segundos. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DespachoMensajesScheduler {

    private final DespachadorMensajes despachador;

    @Scheduled(fixedDelay = 15_000, initialDelay = 10_000)
    @SchedulerLock(name = "despachoMensajes", lockAtMostFor = "PT5M")
    public void despachar() {
        try {
            int enviados = despachador.despacharPendientes();
            if (enviados > 0) {
                log.info("Mensajes enviados: {}", enviados);
            }
        } catch (Exception e) {
            log.error("Error despachando la bandeja de salida: {}", e.getMessage(), e);
        }
    }
}
