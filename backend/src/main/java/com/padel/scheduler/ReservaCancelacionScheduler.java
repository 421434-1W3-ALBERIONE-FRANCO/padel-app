package com.padel.scheduler;

import com.padel.service.ReservaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservaCancelacionScheduler {

    private final ReservaService reservaService;

    // Ejecutar cada 5 minutos (300.000 ms)
    @Scheduled(fixedRate = 300000)
    public void limpiarReservasExpiradas() {
        log.info("Iniciando tarea programada de limpieza de reservas pendientes expiadas");
        try {
            reservaService.limpiarReservasExpiradas();
        } catch (Exception e) {
            log.error("Error durante la limpieza de reservas expiradas: {}", e.getMessage(), e);
        }
    }
}
