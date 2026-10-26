package com.padel.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;

/**
 * Reglas de negocio de las reservas configurables por propiedad ({@code app.reservas.*}). Los valores por
 * defecto son los que el sistema venía usando fijos en el código.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.reservas")
public class ReservasProperties {

    /** Reservas PENDIENTE_PAGO simultáneas que puede tener un jugador (evita acaparar la grilla). */
    private int maxPendientesPorJugador = 2;

    /** Minutos que una reserva puede seguir pendiente de pago antes de cancelarse sola. */
    private int minutosParaPagar = 15;

    /** Las franjas que empiezan a esta hora o después cobran la tarifa nocturna. */
    private LocalTime inicioTarifaNocturna = LocalTime.of(18, 0);

    /** Anticipación mínima (en horas) para que el jugador que cancela reciba el reintegro. */
    private int horasMinimasCancelacionConReintegro = 24;

    /** Si se intenta devolver automáticamente por MercadoPago lo que corresponde reintegrar. */
    private boolean reintegroAutomaticoMercadoPago = true;
}
