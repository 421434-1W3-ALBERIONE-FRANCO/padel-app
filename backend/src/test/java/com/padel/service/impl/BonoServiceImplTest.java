package com.padel.service.impl;

import com.padel.domain.entity.*;
import com.padel.domain.enums.EstadoBono;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.MetodoPago;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.CrearBonoRequest;
import com.padel.dto.response.BonoResponse;
import com.padel.dto.response.PagoResponse;
import com.padel.exception.BonoNoDisponibleException;
import com.padel.exception.ReservaNoModificableException;
import com.padel.mapper.BonoMapper;
import com.padel.mapper.PagoMapper;
import com.padel.repository.*;
import com.padel.service.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void asignarBono_HappyPath() {
        CrearBonoRequest request = new CrearBonoRequest("juan@example.com", "10HS", 10, new BigDecimal("8000.00"), LocalDate.now().plusDays(30));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(bonoRepository.save(any(Bono.class))).thenReturn(bonoMock1);
        BonoResponse responseDto = new BonoResponse(101L, 1L, "juan@example.com", "10HS", 10, 0, new BigDecimal("8000.00"), LocalDate.now().plusDays(30), EstadoBono.ACTIVO, LocalDateTime.now(), LocalDateTime.now());
        when(bonoMapper.toResponse(bonoMock1)).thenReturn(responseDto);

        BonoResponse result = bonoService.asignarBono(request);

        assertNotNull(result);
        assertEquals("10HS", result.tipo());
        verify(bonoRepository).save(any(Bono.class));
    }

    @Test
    void usarBono_HappyPath_DeductsFromCorrectBonoWithRounding() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));

        // List ordered by expiry date: bonoMock2 (expires in 2 days) first, then bonoMock1 (expires in 5 days)
        List<Bono> bonos = new ArrayList<>();
        bonos.add(bonoMock2); // 1 hour left
        bonos.add(bonoMock1); // 10 hours left
        when(bonoRepository.findByUsuarioIdAndEstadoOrderByFechaVencimientoAsc(1L, EstadoBono.ACTIVO)).thenReturn(bonos);

        when(bonoRepository.save(any(Bono.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(usoBonoRepository.save(any(UsoBono.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        Pago pagoMock = Pago.builder().id(50L).reserva(reservaMock).usuario(usuarioMock).monto(new BigDecimal("1200.00")).metodo(MetodoPago.BONO).estado(EstadoPago.APROBADO).build();
        when(pagoRepository.save(any(Pago.class))).thenReturn(pagoMock);

        PagoResponse responseDto = new PagoResponse(50L, 1L, 1L, "juan@example.com", new BigDecimal("1200.00"), MetodoPago.BONO, EstadoPago.APROBADO, null, null, LocalDateTime.now(), LocalDateTime.now());
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
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));

        List<Bono> bonos = List.of(bonoMock2); // only has 1 hour left, needs 2
        when(bonoRepository.findByUsuarioIdAndEstadoOrderByFechaVencimientoAsc(1L, EstadoBono.ACTIVO)).thenReturn(bonos);

        assertThrows(BonoNoDisponibleException.class, () ->
                bonoService.usarBono(1L, "juan@example.com"));

        verify(bonoRepository, never()).save(any());
        verify(usoBonoRepository, never()).save(any());
        verify(reservaService, never()).confirmarReserva(anyLong());
    }

    @Test
    void usarBono_ShouldThrowReservaNoModificableException_WhenNotPending() {
        reservaMock.setEstadoReserva(EstadoReserva.CONFIRMADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));

        assertThrows(ReservaNoModificableException.class, () ->
                bonoService.usarBono(1L, "juan@example.com"));
    }

    @Test
    void expirarBonosVencidos_ShouldMarkVencido() {
        List<Bono> vencibles = List.of(bonoMock1);
        when(bonoRepository.findByEstadoAndFechaVencimientoBefore(eq(EstadoBono.ACTIVO), any(LocalDate.class))).thenReturn(vencibles);

        bonoService.expirarBonosVencidos();

        assertEquals(EstadoBono.VENCIDO, bonoMock1.getEstado());
        verify(bonoRepository).save(bonoMock1);
    }
}
