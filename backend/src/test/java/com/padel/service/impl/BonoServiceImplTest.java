package com.padel.service.impl;

import com.padel.domain.entity.*;
import com.padel.domain.enums.ConceptoPago;
import com.padel.domain.enums.EstadoBono;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.MetodoPago;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.CrearBonoRequest;
import com.padel.dto.response.BonoResponse;
import com.padel.dto.response.PagoResponse;
import com.padel.exception.BonoNoDisponibleException;
import com.padel.exception.DatosInvalidosException;
import com.padel.exception.ReservaNoModificableException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.BonoMapper;
import com.padel.mapper.PagoMapper;
import com.padel.repository.*;
import com.padel.service.ConfiguracionSistemaService;
import com.padel.service.NotificacionInAppService;
import com.padel.service.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BonoServiceImplTest {

    @Mock
    private BonoRepository bonoRepository;
    @Mock
    private UsoBonoRepository usoBonoRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ReservaService reservaService;
    @Mock
    private BonoMapper bonoMapper;
    @Mock
    private PagoMapper pagoMapper;
    @Mock
    private ConfiguracionSistemaService configuracionSistemaService;
    @Mock
    private NotificacionInAppService notificacionInAppService;
    @Mock
    private com.padel.service.ComisionService comisionService;
    @Spy
    private com.padel.security.ControlAcceso controlAcceso = new com.padel.security.ControlAcceso();
    @Mock
    private com.padel.repository.TipoBonoRepository tipoBonoRepository;

    @InjectMocks
    private BonoServiceImpl bonoService;

    private Usuario usuarioMock;
    private Cancha canchaMock;
    private Reserva reservaMock;
    private Bono bonoMock1;
    private Bono bonoMock2;

    @BeforeEach
    void setUp() {
        usuarioMock = Usuario.builder()
                .id(1L)
                .nombre("Juan")
                .email("juan@example.com")
                .rol(RolUsuario.JUGADOR)
                .activo(true)
                .build();

        canchaMock = Cancha.builder()
                .id(1L)
                .nombre("Cancha 1")
                .activa(true)
                .build();

        // 90 minutes turn (18:00 to 19:30) -> should round up to 2 hours
        reservaMock = Reserva.builder()
                .id(1L)
                .usuario(usuarioMock)
                .cancha(canchaMock)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(18, 0))
                .horaFin(LocalTime.of(19, 30))
                .precioTotal(new BigDecimal("1200.00"))
                .estadoReserva(EstadoReserva.PENDIENTE_PAGO)
                .build();

        // Expires in 5 days, has 10 hours total, 0 used -> 10 hours available
        bonoMock1 = Bono.builder()
                .id(101L)
                .usuario(usuarioMock)
                .tipo("10HS")
                .horasTotales(10)
                .horasUsadas(0)
                .precioPagado(new BigDecimal("8000.00"))
                .fechaVencimiento(LocalDate.now().plusDays(5))
                .estado(EstadoBono.ACTIVO)
                .build();

        // Expires in 2 days, has 5 hours total, 4 used -> 1 hour available (insufficient for 2 hours deduction)
        bonoMock2 = Bono.builder()
                .id(102L)
                .usuario(usuarioMock)
                .tipo("5HS")
                .horasTotales(5)
                .horasUsadas(4)
                .precioPagado(new BigDecimal("4500.00"))
                .fechaVencimiento(LocalDate.now().plusDays(2))
                .estado(EstadoBono.ACTIVO)
                .build();
    }

    private com.padel.domain.entity.TipoBono pack10() {
        return com.padel.domain.entity.TipoBono.builder().id(1L).nombre("10HS").horas(10)
                .precio(new BigDecimal("8000.00")).diasVigencia(30).activo(true).build();
    }

    private CrearBonoRequest venta(MetodoPago metodo) {
        return new CrearBonoRequest("juan@example.com", 1L, metodo, false, null);
    }

    @Test
    void asignarBono_HappyPath_TomaHorasPrecioYVencimientoDelCatalogo() {
        Usuario adminMock = Usuario.builder().id(9L).email("admin@example.com").rol(RolUsuario.ADMIN).build();
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(usuarioRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminMock));
        when(tipoBonoRepository.findById(1L)).thenReturn(Optional.of(pack10()));
        when(bonoRepository.save(any(Bono.class))).thenAnswer(inv -> inv.getArgument(0));

        bonoService.asignarBono(venta(MetodoPago.EFECTIVO), "admin@example.com");

        ArgumentCaptor<Bono> bono = ArgumentCaptor.forClass(Bono.class);
        verify(bonoRepository).save(bono.capture());
        assertEquals(10, bono.getValue().getHorasTotales());
        assertEquals(new BigDecimal("8000.00"), bono.getValue().getPrecioPagado());
        assertEquals(LocalDate.now().plusDays(30), bono.getValue().getFechaVencimiento());
        assertEquals("10HS", bono.getValue().getTipo());

        // La venta entra a la caja: pago aprobado del bono, sin reserva, a nombre del comprador.
        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(captor.capture());
        Pago pago = captor.getValue();
        assertEquals(ConceptoPago.BONO, pago.getConcepto());
        assertEquals(MetodoPago.EFECTIVO, pago.getMetodo());
        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        assertEquals(new BigDecimal("8000.00"), pago.getMonto());
        assertNull(pago.getReserva());
        assertSame(usuarioMock, pago.getUsuario());
        assertSame(adminMock, pago.getAtendidoPor());
    }

    @Test
    void asignarBono_SinMedioDePago_SeRechazaSinGuardarNada() {
        Usuario adminMock = Usuario.builder().id(9L).email("admin@example.com").rol(RolUsuario.ADMIN).build();
        when(usuarioRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminMock));
        when(tipoBonoRepository.findById(1L)).thenReturn(Optional.of(pack10()));

        assertThrows(DatosInvalidosException.class, () -> bonoService.asignarBono(venta(null), "admin@example.com"));
        assertThrows(DatosInvalidosException.class, () -> bonoService.asignarBono(venta(MetodoPago.MERCADOPAGO), "admin@example.com"));

        verify(bonoRepository, never()).save(any());
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void asignarBono_SinCargoPorUnAdminConMotivo_NoRegistraPago() {
        Usuario adminMock = Usuario.builder().id(9L).email("admin@example.com").rol(RolUsuario.ADMIN).build();
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(usuarioRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminMock));
        when(tipoBonoRepository.findById(1L)).thenReturn(Optional.of(pack10()));
        when(bonoRepository.save(any(Bono.class))).thenAnswer(inv -> inv.getArgument(0));

        bonoService.asignarBono(new CrearBonoRequest("juan@example.com", 1L, null, true, "Compensación por lluvia"), "admin@example.com");

        ArgumentCaptor<Bono> bono = ArgumentCaptor.forClass(Bono.class);
        verify(bonoRepository).save(bono.capture());
        assertEquals(BigDecimal.ZERO, bono.getValue().getPrecioPagado());
        assertEquals("Compensación por lluvia", bono.getValue().getMotivoSinCargo());
        verify(pagoRepository, never()).save(any());
        verifyNoInteractions(comisionService);
    }

    @Test
    void asignarBono_UnRecepcionistaNoPuedeRegalarBonos() {
        Usuario recepcionistaMock = Usuario.builder().id(9L).email("recepcion@example.com").rol(RolUsuario.RECEPCIONISTA).build();
        when(usuarioRepository.findByEmail("recepcion@example.com")).thenReturn(Optional.of(recepcionistaMock));
        when(tipoBonoRepository.findById(1L)).thenReturn(Optional.of(pack10()));

        assertThrows(com.padel.exception.OperacionNoPermitidaException.class, () -> bonoService.asignarBono(
                new CrearBonoRequest("juan@example.com", 1L, null, true, "Es mi amigo"), "recepcion@example.com"));
        verify(bonoRepository, never()).save(any());
    }

    @Test
    void asignarBono_SinCargoExigeMotivo() {
        Usuario adminMock = Usuario.builder().id(9L).email("admin@example.com").rol(RolUsuario.ADMIN).build();
        when(usuarioRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminMock));
        when(tipoBonoRepository.findById(1L)).thenReturn(Optional.of(pack10()));

        assertThrows(DatosInvalidosException.class, () -> bonoService.asignarBono(
                new CrearBonoRequest("juan@example.com", 1L, null, true, "  "), "admin@example.com"));
    }

    @Test
    void asignarBono_UnTipoDadoDeBajaNoSeVende() {
        Usuario adminMock = Usuario.builder().id(9L).email("admin@example.com").rol(RolUsuario.ADMIN).build();
        com.padel.domain.entity.TipoBono inactivo = pack10();
        inactivo.setActivo(false);
        when(usuarioRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminMock));
        when(tipoBonoRepository.findById(1L)).thenReturn(Optional.of(inactivo));

        assertThrows(com.padel.exception.ResourceNotFoundException.class,
                () -> bonoService.asignarBono(venta(MetodoPago.EFECTIVO), "admin@example.com"));
    }

    @Test
    void asignarBono_ShouldRegisterCommission_WhenSoldByRecepcionistaWithCommissionsEnabled() {
        Usuario recepcionistaMock = Usuario.builder().id(9L).email("recepcion@example.com").rol(RolUsuario.RECEPCIONISTA).build();
        com.padel.domain.entity.ConfiguracionSistema configuracionMock = com.padel.domain.entity.ConfiguracionSistema.builder()
                .id(1L)
                .comisionesHabilitadas(true)
                .porcentajeComisionRecepcion(new BigDecimal("10.00"))
                .build();
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(usuarioRepository.findByEmail("recepcion@example.com")).thenReturn(Optional.of(recepcionistaMock));
        when(tipoBonoRepository.findById(1L)).thenReturn(Optional.of(pack10()));
        when(bonoRepository.save(any(Bono.class))).thenReturn(bonoMock1);
        when(configuracionSistemaService.obtenerEntidad()).thenReturn(configuracionMock);

        bonoService.asignarBono(venta(MetodoPago.TARJETA), "recepcion@example.com");

        verify(comisionService).registrarComisionBono(recepcionistaMock, bonoMock1);
    }

    @Test
    void usarBono_HappyPath_DeductsFromCorrectBonoWithRounding() {
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));

        // List ordered by expiry date: bonoMock2 (expires in 2 days) first, then bonoMock1 (expires in 5 days)
        List<Bono> bonos = new ArrayList<>();
        bonos.add(bonoMock2); // 1 hour left
        bonos.add(bonoMock1); // 10 hours left
        when(bonoRepository.findActivosParaUsar(1L)).thenReturn(bonos);

        when(bonoRepository.save(any(Bono.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(usoBonoRepository.save(any(UsoBono.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        Pago pagoMock = Pago.builder().id(50L).reserva(reservaMock).usuario(usuarioMock).monto(new BigDecimal("1200.00")).metodo(MetodoPago.BONO).estado(EstadoPago.APROBADO).build();
        when(pagoRepository.save(any(Pago.class))).thenReturn(pagoMock);

        PagoResponse responseDto = new PagoResponse(50L, 1L, null, ConceptoPago.TURNO, 1L, "juan@example.com", new BigDecimal("1200.00"), MetodoPago.BONO, EstadoPago.APROBADO, null, null, LocalDateTime.now(), LocalDateTime.now());
        when(pagoMapper.toResponse(any(Pago.class))).thenReturn(responseDto);

        PagoResponse result = bonoService.usarBono(1L, "juan@example.com");

        assertNotNull(result);
        assertEquals(MetodoPago.BONO, result.metodo());
        assertEquals(EstadoPago.APROBADO, result.estado());
        assertEquals(new BigDecimal("1200.00"), result.monto()); // nominal value

        // 90 minutes turn = 1.5 hours -> rounds up to 2 hours.
        // bonoMock2 has 1 hour left (insufficient), so it should skip to bonoMock1.
        // bonoMock1 had 0 used, now should have 2 used.
        assertEquals(2, bonoMock1.getHorasUsadas());
        assertEquals(4, bonoMock2.getHorasUsadas()); // unchanged

        verify(usoBonoRepository).save(any(UsoBono.class));
        verify(pagoRepository).save(any(Pago.class));
        verify(reservaService).confirmarReserva(1L);
    }

    @Test
    void usarBono_ShouldThrowBonoNoDisponibleException_WhenNoBonoHasEnoughBalance() {
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));

        List<Bono> bonos = List.of(bonoMock2); // only has 1 hour left, needs 2
        when(bonoRepository.findActivosParaUsar(1L)).thenReturn(bonos);

        assertThrows(BonoNoDisponibleException.class, () ->
                bonoService.usarBono(1L, "juan@example.com"));

        verify(bonoRepository, never()).save(any());
        verify(usoBonoRepository, never()).save(any());
        verify(reservaService, never()).confirmarReserva(anyLong());
    }

    @Test
    void usarBono_ShouldThrowReservaNoModificableException_WhenNotPending() {
        ReflectionTestUtils.setField(reservaMock, "estadoReserva", EstadoReserva.CONFIRMADA);
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));

        assertThrows(ReservaNoModificableException.class, () ->
                bonoService.usarBono(1L, "juan@example.com"));
    }

    @Test
    void usarBono_UnaReservaConRangoInvalidoNoPuedeSumarHorasAlBono() {
        // Datos heredados: una reserva que "cruzaba" la medianoche tenía fin < inicio y descontaba horas negativas.
        reservaMock.setHoraInicio(LocalTime.of(21, 30));
        reservaMock.setHoraFin(LocalTime.of(0, 30));
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));

        assertThrows(com.padel.exception.RangoHorarioInvalidoException.class, () -> bonoService.usarBono(1L, "juan@example.com"));

        assertEquals(0, bonoMock1.getHorasUsadas());
        verify(bonoRepository, never()).save(any());
    }

    @Test
    void usarBono_TurnoYaCobrado_NoSeDescuentaDeNuevo() {
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(pagoRepository.existsByReservaIdAndEstadoAndConcepto(1L, EstadoPago.APROBADO, ConceptoPago.TURNO)).thenReturn(true);

        assertThrows(com.padel.exception.EstadoInvalidoException.class, () -> bonoService.usarBono(1L, "juan@example.com"));
        verify(bonoRepository, never()).findActivosParaUsar(anyLong());
    }

    @Test
    void usarBono_OtroJugadorNoPuedePagarUnaReservaAjena() {
        Usuario otro = Usuario.builder().id(99L).email("otro@example.com").rol(RolUsuario.JUGADOR).build();
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("otro@example.com")).thenReturn(Optional.of(otro));

        assertThrows(ResourceNotFoundException.class, () -> bonoService.usarBono(1L, "otro@example.com"));
        verify(bonoRepository, never()).findActivosParaUsar(anyLong());
    }

    @Test
    void expirarBonosVencidos_ShouldMarkVencido() {
        List<Bono> vencibles = List.of(bonoMock1);
        when(bonoRepository.findByEstadoAndFechaVencimientoBefore(eq(EstadoBono.ACTIVO), any(LocalDate.class))).thenReturn(vencibles);

        bonoService.expirarBonosVencidos();

        assertEquals(EstadoBono.VENCIDO, bonoMock1.getEstado());
        verify(bonoRepository).save(bonoMock1);
    }

    @Test
    void notificarVencimientosProximos_ShouldCreateNotificationAndMarkFlag() {
        com.padel.domain.entity.ConfiguracionSistema configuracionMock =
                com.padel.domain.entity.ConfiguracionSistema.builder()
                        .id(1L)
                        .diasAvisoVencimientoBono(7)
                        .build();
        when(configuracionSistemaService.obtenerEntidad()).thenReturn(configuracionMock);
        when(bonoRepository.findByEstadoAndFechaVencimientoBetweenAndAvisoVencimientoEnviadoFalse(
                eq(EstadoBono.ACTIVO), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(bonoMock1));

        bonoService.notificarVencimientosProximos();

        assertTrue(bonoMock1.isAvisoVencimientoEnviado());
        verify(notificacionInAppService).crear(eq(usuarioMock), any(), any(), any(), eq(101L));
        verify(bonoRepository).save(bonoMock1);
    }

    @Test
    void notificarVencimientosProximos_ShouldDoNothing_WhenNoBonosFound() {
        com.padel.domain.entity.ConfiguracionSistema configuracionMock =
                com.padel.domain.entity.ConfiguracionSistema.builder()
                        .id(1L)
                        .diasAvisoVencimientoBono(7)
                        .build();
        when(configuracionSistemaService.obtenerEntidad()).thenReturn(configuracionMock);
        when(bonoRepository.findByEstadoAndFechaVencimientoBetweenAndAvisoVencimientoEnviadoFalse(
                eq(EstadoBono.ACTIVO), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        bonoService.notificarVencimientosProximos();

        verify(notificacionInAppService, never()).crear(any(), any(), any(), any(), any());
        verify(bonoRepository, never()).save(any());
    }
}
