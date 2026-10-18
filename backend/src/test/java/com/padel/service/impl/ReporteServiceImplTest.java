package com.padel.service.impl;

import com.padel.domain.entity.Bono;
import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.ConceptoPago;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.MetodoPago;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.response.CajaDiariaResponse;
import com.padel.dto.response.CajaDiariaResponse.MetodoTotal;
import com.padel.dto.response.CajaDiariaResponse.MovimientoCaja;
import com.padel.dto.response.CajaDiariaResponse.PendienteCobro;
import com.padel.repository.PagoRepository;
import com.padel.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteServiceImplTest {

    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private ReservaRepository reservaRepository;

    @InjectMocks
    private ReporteServiceImpl reporteService;

    private final LocalDate hoy = LocalDate.now();
    private Usuario cliente;
    private Usuario recepcionista;
    private Reserva turno;

    @BeforeEach
    void setUp() {
        cliente = Usuario.builder().id(1L).nombre("Juan").apellido("Pérez").email("juan@example.com").rol(RolUsuario.JUGADOR).build();
        recepcionista = Usuario.builder().id(9L).nombre("Ana").apellido("Recepción").email("ana@club.com").rol(RolUsuario.RECEPCIONISTA).build();
        Cancha cancha = Cancha.builder().id(1L).nombre("Cancha Central").build();
        turno = Reserva.builder().id(1L).usuario(cliente).cancha(cancha).fecha(hoy)
                .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30))
                .precioTotal(new BigDecimal("1500.00")).estadoReserva(EstadoReserva.CONFIRMADA).build();
    }

    private Pago pago(long id, ConceptoPago concepto, MetodoPago metodo, String monto, Usuario atendidoPor) {
        return Pago.builder().id(id).reserva(concepto == ConceptoPago.BONO ? null : turno).usuario(cliente)
                .monto(new BigDecimal(monto)).metodo(metodo).estado(EstadoPago.APROBADO)
                .concepto(concepto).atendidoPor(atendidoPor).build();
    }

    @Test
    void obtenerCajaDiaria_SeparaLosTurnosConBonoYCalculaElEfectivoDelArqueo() {
        Bono bono = Bono.builder().id(7L).tipo("10HS").horasTotales(10).build();
        Pago ventaBono = pago(3, ConceptoPago.BONO, MetodoPago.TARJETA, "8000.00", recepcionista);
        ventaBono.setBono(bono);

        List<Pago> pagos = List.of(
                pago(1, ConceptoPago.TURNO, MetodoPago.EFECTIVO, "1500.00", recepcionista),
                pago(2, ConceptoPago.CONSUMOS, MetodoPago.EFECTIVO, "300.00", recepcionista),
                ventaBono,
                pago(4, ConceptoPago.TURNO, MetodoPago.MERCADOPAGO, "1200.00", null),
                pago(5, ConceptoPago.TURNO, MetodoPago.BONO, "1200.00", null));
        when(pagoRepository.findMovimientosDeCaja(EstadoPago.APROBADO, hoy.atStartOfDay(), hoy.atTime(LocalTime.MAX)))
                .thenReturn(pagos);

        Reserva paraCobrarAlLlegar = Reserva.builder().id(2L)
                .usuario(Usuario.builder().id(3L).nombre("Lucía").apellido("Gómez").build())
                .cancha(turno.getCancha()).fecha(hoy).horaInicio(LocalTime.of(20, 0)).horaFin(LocalTime.of(21, 30))
                .precioTotal(new BigDecimal("1800.00")).estadoReserva(EstadoReserva.CONFIRMADA).build();
        when(reservaRepository.findSinCobroDelDia(hoy)).thenReturn(List.of(paraCobrarAlLlegar));

        CajaDiariaResponse caja = reporteService.obtenerCajaDiaria(hoy);

        assertEquals(hoy, caja.fecha());
        // Lo que entró: todo menos el turno pagado con horas de bono (ese dinero entró al vender el bono).
        assertEquals(new BigDecimal("11000.00"), caja.totalIngresos());
        assertEquals(new BigDecimal("1800.00"), caja.totalEfectivo());

        assertEquals(List.of("EFECTIVO", "TARJETA", "MERCADOPAGO", "BONO"),
                caja.detalles().stream().map(MetodoTotal::metodo).toList());
        MetodoTotal efectivo = caja.detalles().get(0);
        assertEquals(new BigDecimal("1800.00"), efectivo.total());
        assertEquals(2, efectivo.cantidad());
        assertTrue(efectivo.ingreso());
        assertFalse(caja.detalles().get(3).ingreso(), "El pago con bono no es ingreso");

        List<MovimientoCaja> movimientos = caja.movimientos();
        assertEquals(5, movimientos.size());
        String turnoTexto = "Cancha Central " + hoy.format(DateTimeFormatter.ofPattern("dd/MM")) + " 18:00";
        assertEquals("Turno · " + turnoTexto, movimientos.get(0).detalle());
        assertEquals("Juan Pérez", movimientos.get(0).cliente());
        assertEquals("Ana Recepción", movimientos.get(0).atendidoPor());
        assertEquals("Consumos · " + turnoTexto, movimientos.get(1).detalle());
        assertEquals("Bono 10HS · 10 hs", movimientos.get(2).detalle());
        assertEquals(ConceptoPago.BONO, movimientos.get(2).concepto());
        assertNull(movimientos.get(3).atendidoPor(), "El pago online no tiene staff");
        assertEquals("Turno con horas de bono · " + turnoTexto, movimientos.get(4).detalle());
        assertFalse(movimientos.get(4).ingreso());

        PendienteCobro pendiente = caja.pendientesDeCobro().get(0);
        assertEquals(2L, pendiente.reservaId());
        assertEquals("Lucía Gómez", pendiente.cliente());
        assertEquals("Cancha Central", pendiente.canchaNombre());
        assertEquals(LocalTime.of(20, 0), pendiente.horaInicio());
        assertEquals(new BigDecimal("1800.00"), pendiente.monto());
    }

    @Test
    void obtenerCajaDiaria_SinMovimientos_DevuelveCerosYListasVacias() {
        when(pagoRepository.findMovimientosDeCaja(EstadoPago.APROBADO, hoy.atStartOfDay(), hoy.atTime(LocalTime.MAX)))
                .thenReturn(List.of());
        when(reservaRepository.findSinCobroDelDia(hoy)).thenReturn(List.of());

        CajaDiariaResponse caja = reporteService.obtenerCajaDiaria(hoy);

        assertEquals(0, BigDecimal.ZERO.compareTo(caja.totalIngresos()));
        assertEquals(0, BigDecimal.ZERO.compareTo(caja.totalEfectivo()));
        assertTrue(caja.detalles().isEmpty());
        assertTrue(caja.movimientos().isEmpty());
        assertTrue(caja.pendientesDeCobro().isEmpty());
    }
}
