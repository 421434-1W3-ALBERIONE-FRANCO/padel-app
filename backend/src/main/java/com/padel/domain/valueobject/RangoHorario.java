package com.padel.domain.valueobject;

import com.padel.exception.RangoHorarioInvalidoException;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Rango horario [inicio, fin) dentro de un mismo día. El invariante fin &gt; inicio vive acá y no
 * repartido en cada servicio: un turno que pasaba la medianoche (21:30 + 180 min = 00:30) quedaba con
 * fin &lt; inicio y rompía en silencio los controles de superposición, el precio y el descuento de bonos.
 */
public record RangoHorario(LocalTime inicio, LocalTime fin) {

    private static final int MINUTOS_POR_DIA = 24 * 60;

    public RangoHorario {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fin, "fin");
        if (!fin.isAfter(inicio)) {
            throw new RangoHorarioInvalidoException(
                    "La hora de fin (" + fin + ") debe ser posterior a la de inicio (" + inicio + ") dentro del mismo día.");
        }
    }

    /** Rango de {@code minutos} desde {@code inicio}; rechaza duraciones nulas, negativas o que pasen la medianoche. */
    public static RangoHorario desde(LocalTime inicio, int minutos) {
        if (!cabeEnElDia(inicio, minutos)) {
            throw new RangoHorarioInvalidoException(
                    "Una duración de " + minutos + " min desde las " + inicio + " no entra en el día.");
        }
        return new RangoHorario(inicio, inicio.plusMinutes(minutos));
    }

    /** Si {@code minutos} desde {@code inicio} es una duración válida que termina antes de la medianoche. */
    public static boolean cabeEnElDia(LocalTime inicio, int minutos) {
        if (inicio == null || minutos <= 0) {
            return false;
        }
        int inicioEnMinutos = inicio.toSecondOfDay() / 60;
        return inicioEnMinutos + minutos < MINUTOS_POR_DIA;
    }

    public boolean seSuperponeCon(LocalTime otroInicio, LocalTime otroFin) {
        return inicio.isBefore(otroFin) && otroInicio.isBefore(fin);
    }

    public boolean seSuperponeCon(RangoHorario otro) {
        return seSuperponeCon(otro.inicio(), otro.fin());
    }

    public long minutos() {
        return Duration.between(inicio, fin).toMinutes();
    }
}
