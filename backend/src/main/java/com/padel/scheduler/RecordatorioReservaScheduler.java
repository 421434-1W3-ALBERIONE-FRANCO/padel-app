package com.padel.scheduler;

import com.padel.service.ReservaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecordatorioReservaScheduler {

    private final ReservaService reservaService;

    // Ejecutar todos los días a las 8:00 AM
    @Scheduled(cron = "0 0 8 * * ?")
    @SchedulerLock(name = "recordatorioReservas", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void enviarRecordatorios() {
        log.info("Iniciando tarea programada de recordatorio de reservas de hoy");
        try {
            reservaService.notificarReservasDeHoy();
        } catch (Exception e) {
            log.error("Error durante el envío de recordatorios de reserva: {}", e.getMessage(), e);
        }
    }
}
