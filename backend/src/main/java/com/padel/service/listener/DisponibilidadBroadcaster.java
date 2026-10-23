package com.padel.service.listener;

import com.padel.domain.evento.DisponibilidadCambiadaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Difunde por WebSocket que cambió la grilla de una cancha. Corre después del commit: antes el aviso salía
 * dentro de la transacción y un cliente podía releer la disponibilidad antes de que el cambio existiera
 * (o de un cambio que después se revertía).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DisponibilidadBroadcaster {

    static final String TOPICO = "/topic/disponibilidad";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void difundir(DisponibilidadCambiadaEvent evento) {
        try {
            messagingTemplate.convertAndSend(TOPICO, Map.of("type", "availability_update", "canchaId", evento.canchaId()));
        } catch (RuntimeException e) {
            // El aviso en vivo es un extra: si falla, la grilla se actualiza igual en el próximo refresco.
            log.warn("No se pudo difundir el cambio de disponibilidad de la cancha {}: {}", evento.canchaId(), e.getMessage());
        }
    }
}
