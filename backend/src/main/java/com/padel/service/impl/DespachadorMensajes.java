package com.padel.service.impl;

import com.padel.domain.entity.MensajeSaliente;
import com.padel.domain.enums.EstadoMensaje;
import com.padel.integracion.mensajeria.EnviadorEmail;
import com.padel.integracion.mensajeria.EnviadorWhatsApp;
import com.padel.repository.MensajeSalienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Envía los mensajes pendientes de la bandeja de salida, cada uno en su propia transacción, con reintentos
 * espaciados (1 min, 5 min, 15 min, 1 h, 6 h). Después del último queda en ERROR para revisarlo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DespachadorMensajes {

    static final List<Duration> ESPERAS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(6));
    static final int MAX_INTENTOS = ESPERAS.size() + 1;

    private final MensajeSalienteRepository repository;
    private final EnviadorEmail enviadorEmail;
    private final EnviadorWhatsApp enviadorWhatsApp;
    private final TransactionTemplate transactionTemplate;

    /** Devuelve cuántos mensajes se enviaron en esta pasada. */
    public int despacharPendientes() {
        List<Long> pendientes = transactionTemplate.execute(status ->
                repository.findTop50ByEstadoAndProximoIntentoEnLessThanEqualOrderByIdAsc(EstadoMensaje.PENDIENTE, LocalDateTime.now())
                        .stream().map(MensajeSaliente::getId).toList());
        int enviados = 0;
        for (Long id : pendientes == null ? List.<Long>of() : pendientes) {
            if (Boolean.TRUE.equals(transactionTemplate.execute(status -> enviar(id)))) {
                enviados++;
            }
        }
        return enviados;
    }

    private boolean enviar(Long id) {
        MensajeSaliente mensaje = repository.findById(id)
                .filter(m -> m.getEstado() == EstadoMensaje.PENDIENTE)
                .orElse(null);
        if (mensaje == null) {
            return false;
        }
        mensaje.setIntentos(mensaje.getIntentos() + 1);
        try {
            switch (mensaje.getCanal()) {
                case EMAIL -> enviadorEmail.enviar(mensaje.getDestinatario(), mensaje.getAsunto(), mensaje.getCuerpo());
                case WHATSAPP -> enviadorWhatsApp.enviar(mensaje.getDestinatario(), mensaje.getCuerpo());
            }
            mensaje.setEstado(EstadoMensaje.ENVIADO);
            mensaje.setEnviadoEn(LocalDateTime.now());
            mensaje.setUltimoError(null);
            return true;
        } catch (RuntimeException e) {
            mensaje.setUltimoError(recortar(e.getMessage()));
            if (mensaje.getIntentos() >= MAX_INTENTOS) {
                mensaje.setEstado(EstadoMensaje.ERROR);
                log.error("Mensaje ID {} ({}) descartado tras {} intentos: {}", id, mensaje.getCanal(), mensaje.getIntentos(), e.getMessage());
            } else {
                mensaje.setProximoIntentoEn(LocalDateTime.now().plus(ESPERAS.get(mensaje.getIntentos() - 1)));
                log.warn("Falló el intento {} del mensaje ID {} ({}): {}", mensaje.getIntentos(), id, mensaje.getCanal(), e.getMessage());
            }
            return false;
        } finally {
            repository.save(mensaje);
        }
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return "Error sin detalle";
        }
        return texto.length() <= 500 ? texto : texto.substring(0, 500);
    }
}
