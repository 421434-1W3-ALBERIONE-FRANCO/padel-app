package com.padel.domain;

import com.padel.domain.entity.Bono;
import com.padel.domain.entity.Producto;
import com.padel.domain.entity.Reserva;
import com.padel.domain.enums.EstadoBono;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.valueobject.RangoHorario;
import com.padel.exception.BonoNoDisponibleException;
import com.padel.exception.DatosInvalidosException;
import com.padel.exception.RangoHorarioInvalidoException;
import com.padel.exception.ReservaNoModificableException;
import com.padel.exception.StockInsuficienteException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Invariantes del dominio que antes vivían (o faltaban) en los servicios. */
class RangoHorarioYBonoTest {

    @Test
    void unRangoQuePasaLaMedianocheNoSePuedeCrear() {
        assertFalse(RangoHorario.cabeEnElDia(LocalTime.of(21, 30), 180));
        assertFalse(RangoHorario.cabeEnElDia(LocalTime.of(22, 30), 90)); // termina justo a las 24:00
        assertTrue(RangoHorario.cabeEnElDia(LocalTime.of(21, 30), 90));
        assertThrows(RangoHorarioInvalidoException.class, () -> RangoHorario.desde(LocalTime.of(21, 30), 180));
        assertThrows(RangoHorarioInvalidoException.class, () -> new RangoHorario(LocalTime.of(21, 30), LocalTime.of(0, 30)));
    }

    @Test
    void duracionesNulasONegativasNoSonRangosValidos() {
        assertFalse(RangoHorario.cabeEnElDia(LocalTime.of(10, 0), 0));
        assertFalse(RangoHorario.cabeEnElDia(LocalTime.of(10, 0), -60));
    }

    @Test
    void superposicionEsSemiabierta() {
        RangoHorario r = RangoHorario.desde(LocalTime.of(18, 0), 90);
        assertEquals(90, r.minutos());
        assertTrue(r.seSuperponeCon(LocalTime.of(19, 0), LocalTime.of(20, 0)));
        assertFalse(r.seSuperponeCon(LocalTime.of(19, 30), LocalTime.of(21, 0))); // empieza justo cuando termina
        assertFalse(r.seSuperponeCon(LocalTime.of(16, 30), LocalTime.of(18, 0)));
    }

    @Test
    void laReservaSoloCambiaDeEstadoPorTransicionesValidas() {
        Reserva reserva = Reserva.builder().build();
        assertEquals(EstadoReserva.PENDIENTE_PAGO, reserva.getEstadoReserva());
        reserva.confirmar();
        reserva.completar();
        assertThrows(ReservaNoModificableException.class, () -> reserva.cancelar("tarde"));
        assertThrows(ReservaNoModificableException.class, reserva::confirmar);
    }

    @Test
    void unBonoNoPuedeGanarHorasAlDescontar() {
        Bono bono = bono(10, 4);
        assertThrows(DatosInvalidosException.class, () -> bono.descontarHoras(-21));
        assertThrows(DatosInvalidosException.class, () -> bono.descontarHoras(0));
        assertEquals(4, bono.getHorasUsadas());
    }

    @Test
    void descontarMasHorasQueElSaldoSeRechazaYAlAgotarseCambiaDeEstado() {
        Bono bono = bono(4, 2);
        assertThrows(BonoNoDisponibleException.class, () -> bono.descontarHoras(3));
        bono.descontarHoras(2);
        assertEquals(EstadoBono.AGOTADO, bono.getEstado());
    }

    @Test
    void reintegrarHorasReactivaUnBonoAgotadoQueNoVencio() {
        Bono bono = bono(4, 4);
        bono.setEstado(EstadoBono.AGOTADO);
        bono.reintegrarHoras(2, LocalDate.now());
        assertEquals(2, bono.getHorasUsadas());
        assertEquals(EstadoBono.ACTIVO, bono.getEstado());
    }

    @Test
    void elStockNoPuedeQuedarNegativo() {
        Producto producto = Producto.builder().nombre("Agua").stock(2).precio(BigDecimal.ONE).build();
        assertThrows(StockInsuficienteException.class, () -> producto.descontarStock(3));
        producto.descontarStock(2);
        assertEquals(0, producto.getStock());
    }

    private Bono bono(int totales, int usadas) {
        return Bono.builder().horasTotales(totales).horasUsadas(usadas).estado(EstadoBono.ACTIVO)
                .fechaVencimiento(LocalDate.now().plusDays(30)).precioPagado(BigDecimal.TEN).tipo("10HS").build();
    }
}
