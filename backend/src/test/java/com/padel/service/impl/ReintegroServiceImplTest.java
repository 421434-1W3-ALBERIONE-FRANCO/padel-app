package com.padel.service.impl;

import com.padel.config.ReservasProperties;
import com.padel.domain.entity.Bono;
import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.UsoBono;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.ConceptoPago;
import com.padel.domain.enums.EstadoBono;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.MetodoPago;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.domain.evento.OrigenCancelacion;
import com.padel.exception.EstadoInvalidoException;
import com.padel.integracion.pagos.PasarelaPagos;
import com.padel.integracion.pagos.PasarelaPagosException;
import com.padel.mapper.PagoMapper;
import com.padel.repository.BonoRepository;
import com.padel.repository.PagoRepository;
import com.padel.repository.UsoBonoRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.NotificacionInAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReintegroServiceImplTest {

    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private UsoBonoRepository usoBonoRepository;
    @Mock
    private BonoRepository bonoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasarelaPagos pasarelaPagos;
    @Mock
    private NotificacionInAppService notificacionInAppService;
    @Mock
    private PagoMapper pagoMapper;
    @Spy
    private ReservasProperties reservasProperties = new ReservasProperties();

    @InjectMocks
    private ReintegroServiceImpl reintegroService;

    private Usuario jugador;
    private Reserva reserva;

    @BeforeEach
    void setUp() {
        jugador = Usuario.builder().id(1L).nombre("Juan").apellido("Perez").rol(RolUsuario.JUGADOR).activo(true).build();
        reserva = Reserva.builder().id(1L).usuario(jugador).fecha(LocalDate.now().plusDays(3))
                .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30)).precioTotal(new BigDecimal("1500.00")).build();
    }

    private Pago cobro(MetodoPago metodo) {
        return Pago.builder().id(10L).reserva(reserva).usuario(jugador).monto(new BigDecimal("1500.00"))
                .metodo(metodo).estado(EstadoPago.APROBADO).concepto(ConceptoPago.TURNO).mpPaymentId("555").build();
    }

    @Test
    void sinCobrosNoHayNadaQueReintegrar() {
        when(pagoRepository.findAllByReservaIdAndEstadoAndConcepto(1L, EstadoPago.APROBADO, ConceptoPago.TURNO)).thenReturn(List.of());

        assertEquals("", reintegroService.reintegrarCancelacion(reserva, OrigenCancelacion.JUGADOR));
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void porFaltaDePagoNiSiquieraBuscaCobros() {
        assertEquals("", reintegroService.reintegrarCancelacion(reserva, OrigenCancelacion.FALTA_DE_PAGO));
        verifyNoInteractions(pagoRepository);
    }

    @Test
    void elJugadorQueCancelaTardeNoRecibeReintegro() {
        reserva.setFecha(LocalDate.now());
        reserva.setHoraInicio(LocalTime.now().plusHours(2).withNano(0));
        reserva.setHoraFin(reserva.getHoraInicio().plusMinutes(1));
        when(pagoRepository.findAllByReservaIdAndEstadoAndConcepto(1L, EstadoPago.APROBADO, ConceptoPago.TURNO))
                .thenReturn(List.of(cobro(MetodoPago.MERCADOPAGO)));

        String detalle = reintegroService.reintegrarCancelacion(reserva, OrigenCancelacion.JUGADOR);

        assertTrue(detalle.contains("no tiene reintegro"));
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void siCancelaElClubSiempreHayReintegroAunqueFalteMenosDeUnDia() {
        reserva.setFecha(LocalDate.now());
        reserva.setHoraInicio(LocalTime.now().plusHours(1).withNano(0));
        when(pagoRepository.findAllByReservaIdAndEstadoAndConcepto(1L, EstadoPago.APROBADO, ConceptoPago.TURNO))
                .thenReturn(List.of(cobro(MetodoPago.MERCADOPAGO)));

        reintegroService.reintegrarCancelacion(reserva, OrigenCancelacion.CLUB);

        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(captor.capture());
        assertEquals(ConceptoPago.REINTEGRO, captor.getValue().getConcepto());
        assertEquals(new BigDecimal("-1500.00"), captor.getValue().getMonto());
    }

    @Test
    void unPagoPorMercadoPagoSeEncolaParaDevolverseSolo() {
        Pago cobro = cobro(MetodoPago.MERCADOPAGO);
        when(pagoRepository.findAllByReservaIdAndEstadoAndConcepto(1L, EstadoPago.APROBADO, ConceptoPago.TURNO)).thenReturn(List.of(cobro));

        String detalle = reintegroService.reintegrarCancelacion(reserva, OrigenCancelacion.JUGADOR);

        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(captor.capture());
        assertEquals(EstadoPago.PENDIENTE, captor.getValue().getEstado());
        assertEquals(MetodoPago.MERCADOPAGO, captor.getValue().getMetodo());
        assertEquals(cobro, captor.getValue().getPagoOriginal());
        assertTrue(detalle.contains("MercadoPago"));
        verifyNoInteractions(pasarelaPagos); // la devolución la hace el proceso programado, fuera de la transacción
    }

    @Test
    void unTurnoPagadoConBonoDevuelveLasHorasAlBono() {
        Bono bono = Bono.builder().id(7L).horasTotales(4).horasUsadas(4).estado(EstadoBono.AGOTADO)
                .fechaVencimiento(LocalDate.now().plusDays(30)).build();
        when(pagoRepository.findAllByReservaIdAndEstadoAndConcepto(1L, EstadoPago.APROBADO, ConceptoPago.TURNO))
                .thenReturn(List.of(cobro(MetodoPago.BONO)));
        when(usoBonoRepository.findByReservaId(1L)).thenReturn(List.of(UsoBono.builder().bono(bono).horasDescontadas(2).build()));
        when(bonoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(bono));

        String detalle = reintegroService.reintegrarCancelacion(reserva, OrigenCancelacion.JUGADOR);

        assertEquals(2, bono.getHorasUsadas());
        assertEquals(EstadoBono.ACTIVO, bono.getEstado());
        assertTrue(detalle.contains("2 horas"));
    }

    @Test
    void unCobroEnEfectivoQuedaPendienteDeEntregaYSeAvisaAlStaff() {
        Usuario recepcion = Usuario.builder().id(4L).rol(RolUsuario.RECEPCIONISTA).activo(true).build();
        when(pagoRepository.findAllByReservaIdAndEstadoAndConcepto(1L, EstadoPago.APROBADO, ConceptoPago.TURNO))
                .thenReturn(List.of(cobro(MetodoPago.EFECTIVO)));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findByRol(RolUsuario.ADMIN)).thenReturn(List.of());
        when(usuarioRepository.findByRol(RolUsuario.RECEPCIONISTA)).thenReturn(List.of(recepcion));

        String detalle = reintegroService.reintegrarCancelacion(reserva, OrigenCancelacion.JUGADOR);

        assertTrue(detalle.contains("Acercate al club"));
        verify(notificacionInAppService).crear(eq(recepcion), eq(TipoNotificacion.REINTEGRO_PENDIENTE), anyString(), anyString(), any());
    }

    @Test
    void unCobroYaReintegradoNoSeDevuelveDosVeces() {
        when(pagoRepository.findAllByReservaIdAndEstadoAndConcepto(1L, EstadoPago.APROBADO, ConceptoPago.TURNO))
                .thenReturn(List.of(cobro(MetodoPago.MERCADOPAGO)));
        when(pagoRepository.existsByPagoOriginalId(10L)).thenReturn(true);

        reintegroService.reintegrarCancelacion(reserva, OrigenCancelacion.CLUB);

        verify(pagoRepository, never()).save(any());
    }

    @Test
    void elProcesoAutomaticoDevuelvePorMercadoPagoConClaveDeIdempotencia() {
        Pago original = cobro(MetodoPago.MERCADOPAGO);
        Pago reintegro = Pago.builder().id(20L).usuario(jugador).monto(new BigDecimal("-1500.00")).metodo(MetodoPago.MERCADOPAGO)
                .estado(EstadoPago.PENDIENTE).concepto(ConceptoPago.REINTEGRO).pagoOriginal(original).build();
        when(pagoRepository.findTop20ByConceptoAndEstadoAndMetodoAndIntentosReintegroLessThanOrderByIdAsc(
                ConceptoPago.REINTEGRO, EstadoPago.PENDIENTE, MetodoPago.MERCADOPAGO, 3)).thenReturn(List.of(reintegro));

        reintegroService.procesarReintegrosAutomaticos();

        verify(pasarelaPagos).reembolsar("555", "reintegro-pago-10");
        assertEquals(EstadoPago.APROBADO, reintegro.getEstado());
        assertEquals(1, reintegro.getIntentosReintegro());
    }

    @Test
    void trasVariosFallosElReintegroQuedaParaResolverAMano() {
        Usuario admin = Usuario.builder().id(9L).rol(RolUsuario.ADMIN).activo(true).build();
        Pago reintegro = Pago.builder().id(20L).usuario(jugador).monto(new BigDecimal("-1500.00")).metodo(MetodoPago.MERCADOPAGO)
                .estado(EstadoPago.PENDIENTE).concepto(ConceptoPago.REINTEGRO).pagoOriginal(cobro(MetodoPago.MERCADOPAGO))
                .intentosReintegro(2).build();
        when(pagoRepository.findTop20ByConceptoAndEstadoAndMetodoAndIntentosReintegroLessThanOrderByIdAsc(
                ConceptoPago.REINTEGRO, EstadoPago.PENDIENTE, MetodoPago.MERCADOPAGO, 3)).thenReturn(List.of(reintegro));
        doThrow(new PasarelaPagosException("timeout")).when(pasarelaPagos).reembolsar(anyString(), anyString());
        when(usuarioRepository.findByRol(RolUsuario.ADMIN)).thenReturn(List.of(admin));
        when(usuarioRepository.findByRol(RolUsuario.RECEPCIONISTA)).thenReturn(List.of());

        reintegroService.procesarReintegrosAutomaticos();

        assertEquals(EstadoPago.PENDIENTE, reintegro.getEstado());
        assertEquals(3, reintegro.getIntentosReintegro());
        verify(notificacionInAppService).crear(eq(admin), eq(TipoNotificacion.REINTEGRO_PENDIENTE), anyString(), anyString(), eq(20L));
    }

    @Test
    void registrarEntregaMarcaElReintegroComoAprobadoYNoAdmiteRepetirse() {
        Usuario recepcion = Usuario.builder().id(4L).email("recepcion@example.com").rol(RolUsuario.RECEPCIONISTA).build();
        Pago reintegro = Pago.builder().id(20L).usuario(jugador).monto(new BigDecimal("-1500.00")).metodo(MetodoPago.EFECTIVO)
                .estado(EstadoPago.PENDIENTE).concepto(ConceptoPago.REINTEGRO).build();
        when(pagoRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(reintegro));
        when(usuarioRepository.findByEmail("recepcion@example.com")).thenReturn(Optional.of(recepcion));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        reintegroService.registrarEntrega(20L, "recepcion@example.com");

        assertEquals(EstadoPago.APROBADO, reintegro.getEstado());
        assertEquals(recepcion, reintegro.getAtendidoPor());
        assertThrows(EstadoInvalidoException.class, () -> reintegroService.registrarEntrega(20L, "recepcion@example.com"));
    }

    @Test
    void unPagoTardioSeDevuelveYSeLeAvisaAlJugador() {
        Pago tardio = cobro(MetodoPago.MERCADOPAGO);
        tardio.setCreatedAt(LocalDateTime.now());

        reintegroService.reintegrarPagoTardio(tardio);

        verify(pagoRepository).save(any(Pago.class));
        verify(notificacionInAppService).crear(eq(jugador), eq(TipoNotificacion.REINTEGRO), anyString(), anyString(), eq(1L));
    }
}
