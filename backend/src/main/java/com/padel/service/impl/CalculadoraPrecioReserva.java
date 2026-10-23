package com.padel.service.impl;

import com.padel.config.ReservasProperties;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.domain.valueobject.RangoHorario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Cálculo de precios y cobertura horaria de una reserva. Antes estaba duplicado en ReservaServiceImpl y
 * DisponibilidadServiceImpl (con el corte nocturno de las 18:00 fijo en los dos): un cambio en uno y no
 * en el otro hacía que la grilla mostrara un precio y la reserva cobrara otro.
 */
@Component
@RequiredArgsConstructor
public class CalculadoraPrecioReserva {

    private final ReservasProperties reservasProperties;

    /** Franjas que rigen ese día de la semana, ordenadas por hora de inicio. */
    public List<FranjaHoraria> franjasDelDia(List<FranjaHoraria> franjasCancha, DayOfWeek dia) {
        return franjasCancha.stream()
                .filter(f -> f.aplicaEn(dia))
                .sorted(Comparator.comparing(FranjaHoraria::getHoraInicio))
                .toList();
    }

    public boolean esNocturna(FranjaHoraria franja) {
        return !franja.getHoraInicio().isBefore(reservasProperties.getInicioTarifaNocturna());
    }

    /** Tarifa completa de la franja (base o nocturna según su hora de inicio). */
    public BigDecimal tarifa(FranjaHoraria franja) {
        return esNocturna(franja) ? franja.getPrecioNocturno() : franja.getPrecioBase();
    }

    /**
     * Fin del horario de apertura contiguo desde {@code desde}: encadena franjas que se tocan sin huecos.
     */
    public LocalTime finDeAperturaContigua(List<FranjaHoraria> franjasDelDia, LocalTime desde) {
        LocalTime fin = desde;
        boolean extendido = true;
        while (extendido) {
            extendido = false;
            for (FranjaHoraria f : franjasDelDia) {
                if (!f.getHoraInicio().isAfter(fin) && f.getHoraFin().isAfter(fin)) {
                    fin = f.getHoraFin();
                    extendido = true;
                }
            }
        }
        return fin;
    }

    /** Si el rango entra completo dentro del horario de apertura (franjas contiguas, sin huecos). */
    public boolean estaCubierto(List<FranjaHoraria> franjasDelDia, RangoHorario rango) {
        boolean empiezaEnUnaFranja = franjasDelDia.stream()
                .anyMatch(f -> !f.getHoraInicio().isAfter(rango.inicio()) && f.getHoraFin().isAfter(rango.inicio()));
        return empiezaEnUnaFranja && !finDeAperturaContigua(franjasDelDia, rango.inicio()).isBefore(rango.fin());
    }

    /**
     * Precio prorrateado: cada franja que el rango cubre aporta su tarifa por la fracción ocupada. Vacío si el
     * rango no está cubierto por completo por el horario de apertura.
     */
    public Optional<BigDecimal> calcular(List<FranjaHoraria> franjasDelDia, RangoHorario rango) {
        if (!estaCubierto(franjasDelDia, rango)) {
            return Optional.empty();
        }
        BigDecimal total = BigDecimal.ZERO;
        for (FranjaHoraria f : franjasDelDia) {
            if (!rango.seSuperponeCon(f.getHoraInicio(), f.getHoraFin())) {
                continue;
            }
            LocalTime desde = rango.inicio().isAfter(f.getHoraInicio()) ? rango.inicio() : f.getHoraInicio();
            LocalTime hasta = rango.fin().isBefore(f.getHoraFin()) ? rango.fin() : f.getHoraFin();
            long minutos = Duration.between(desde, hasta).toMinutes();
            BigDecimal fraccion = BigDecimal.valueOf(minutos)
                    .divide(BigDecimal.valueOf(f.getDuracionMin()), 6, RoundingMode.HALF_UP);
            total = total.add(tarifa(f).multiply(fraccion));
        }
        return Optional.of(total.setScale(2, RoundingMode.HALF_UP));
    }
}
