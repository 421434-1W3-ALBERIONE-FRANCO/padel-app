package com.padel.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Límites de pedidos ({@code app.limites.*}). Los valores por defecto están pensados para un club: holgados
 * para el uso normal (varios jugadores detrás del wifi del club comparten IP) y cortos para un script.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.limites")
public class LimitesProperties {

    /** Apaga todos los límites (solo para tests que no los prueban). */
    private boolean habilitado = true;

    /** Usar Redis (compartido entre instancias) o un contador en memoria. */
    private boolean usarRedis = true;

    /** Intentos de login por IP. */
    private Regla login = new Regla(30, Duration.ofMinutes(5));

    /** Intentos fallidos por cuenta antes de bloquearla temporalmente. */
    private Regla intentosFallidosPorCuenta = new Regla(5, Duration.ofMinutes(15));

    /** Registros nuevos por IP. */
    private Regla registro = new Regla(10, Duration.ofHours(1));

    /** Pedidos de recuperación de contraseña por IP. */
    private Regla recuperacion = new Regla(10, Duration.ofHours(1));

    /** Emails de recuperación o verificación que se mandan a una misma cuenta. */
    private Regla emailsPorCuenta = new Regla(3, Duration.ofHours(1));

    /** Usos de un token (resetear contraseña, verificar email) por IP. */
    private Regla tokens = new Regla(20, Duration.ofHours(1));

    /** Reservas que un jugador puede crear por hora (además del tope de pendientes de pago). */
    private Regla reservasPorJugador = new Regla(20, Duration.ofHours(1));

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Regla {
        private int maximo;
        private Duration ventana;
    }
}
