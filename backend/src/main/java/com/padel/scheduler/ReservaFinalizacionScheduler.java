package com.padel.scheduler;

import com.padel.service.ReservaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservaFinalizacionScheduler {

    private final ReservaService reservaService;

    // Ejecutar cada 15 minutos (900.000 ms) y también al arrancar: pasa a COMPLETADA las reservas
    // confirmadas cuyo turno ya terminó, incluidas las que quedaron pendientes mientras el servidor estuvo apagado.
    @Scheduled(fixedRate = 900000)
    public void completarReservasFinalizadas() {
        log.info("Iniciando tarea programada de cierre de reservas con turno finalizado");
        try {
            reservaService.completarReservasFinalizadas();
        } catch (Exception e) {
            log.error("Error durante el cierre de reservas finalizadas: {}", e.getMessage(), e);
        }
    }
}
