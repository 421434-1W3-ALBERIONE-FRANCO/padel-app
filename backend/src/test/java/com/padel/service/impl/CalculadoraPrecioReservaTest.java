package com.padel.service.impl;

import com.padel.config.ReservasProperties;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.domain.valueobject.RangoHorario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculadoraPrecioReservaTest {

    private final CalculadoraPrecioReserva calculadora = new CalculadoraPrecioReserva(new ReservasProperties());

    private final FranjaHoraria tarde = franja(LocalTime.of(16, 30), LocalTime.of(18, 0), "1000", "9999", "MONDAY,TUESDAY");
    private final FranjaHoraria noche = franja(LocalTime.of(18, 0), LocalTime.of(19, 30), "9999", "1500", "MONDAY,TUESDAY");
    private final FranjaHoraria soloSabado = franja(LocalTime.of(10, 0), LocalTime.of(11, 30), "800", "800", "SATURDAY");

    @Test
    void filtraYOrdenaLasFranjasDelDia() {
        List<FranjaHoraria> lunes = calculadora.franjasDelDia(List.of(noche, soloSabado, tarde), DayOfWeek.MONDAY);
        assertEquals(List.of(tarde, noche), lunes);
    }

    @Test
    void laTarifaNocturnaArrancaEnElCorteConfigurado() {
        assertEquals(new BigDecimal("1000"), calculadora.tarifa(tarde));
        assertEquals(new BigDecimal("1500"), calculadora.tarifa(noche));

        ReservasProperties props = new ReservasProperties();
        props.setInicioTarifaNocturna(LocalTime.of(16, 0));
        assertEquals(new BigDecimal("9999"), new CalculadoraPrecioReserva(props).tarifa(tarde));
    }

    @Test
    void prorrateaCuandoElTurnoCruzaFranjas() {
        // 17:15-18:45: mitad de la franja de la tarde (500) + mitad de la nocturna (750).
        Optional<BigDecimal> precio = calculadora.calcular(List.of(tarde, noche), RangoHorario.desde(LocalTime.of(17, 15), 90));
        assertEquals(new BigDecimal("1250.00"), precio.orElseThrow());
    }

    @Test
    void sinCoberturaCompletaNoHayPrecio() {
        assertTrue(calculadora.calcular(List.of(tarde, noche), RangoHorario.desde(LocalTime.of(18, 0), 120)).isEmpty());
        assertTrue(calculadora.calcular(List.of(tarde, noche), RangoHorario.desde(LocalTime.of(15, 0), 60)).isEmpty());
    }

    @Test
    void elFinDeAperturaEncadenaFranjasContiguas() {
        assertEquals(LocalTime.of(19, 30), calculadora.finDeAperturaContigua(List.of(tarde, noche), LocalTime.of(17, 0)));
    }

    private static FranjaHoraria franja(LocalTime inicio, LocalTime fin, String base, String nocturno, String dias) {
        return FranjaHoraria.builder().horaInicio(inicio).horaFin(fin)
                .duracionMin((int) Duration.between(inicio, fin).toMinutes())
                .precioBase(new BigDecimal(base)).precioNocturno(new BigDecimal(nocturno)).diasAplicables(dias).build();
    }
}
