package com.padel.service.impl;

import com.padel.domain.entity.*;
import com.padel.domain.enums.EstadoComision;
import com.padel.domain.enums.EstadoSesionTrabajo;
import com.padel.domain.enums.OrigenComision;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.LiquidacionRequest;
import com.padel.dto.response.LiquidacionResponse;
import com.padel.exception.DatosInvalidosException;
import com.padel.mapper.LiquidacionSueldoMapper;
import com.padel.repository.*;
import com.padel.service.ConfiguracionSistemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiquidacionServiceImplTest {

    @Mock
    private LiquidacionSueldoRepository liquidacionSueldoRepository;
    @Mock
    private SesionTrabajoRepository sesionTrabajoRepository;
    @Mock
    private MovimientoBancoHorasRepository movimientoBancoHorasRepository;
    @Mock
    private BancoHorasRepository bancoHorasRepository;
    @Mock
    private ComisionRepository comisionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ConfiguracionSistemaService configuracionSistemaService;
    @Mock
    private LiquidacionSueldoMapper liquidacionSueldoMapper;

    @InjectMocks
    private LiquidacionServiceImpl liquidacionService;

    private Usuario recepcionistaMock;
    private Usuario adminMock;

    @BeforeEach
    void setUp() {
        recepcionistaMock = Usuario.builder().id(1L).email("recepcion@example.com").rol(RolUsuario.RECEPCIONISTA).build();
        adminMock = Usuario.builder().id(2L).email("admin@example.com").rol(RolUsuario.ADMIN).build();
    }

    @Test
    void liquidar_ShouldThrowDatosInvalidos_WhenHastaBeforeDesde() {
        LiquidacionRequest request = new LiquidacionRequest(1L, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 1));

        assertThrows(DatosInvalidosException.class, () -> liquidacionService.liquidar(request, "admin@example.com"));
    }

    @Test
    void liquidar_ShouldThrowDatosInvalidos_WhenEmpleadoIsJugador() {
        Usuario jugador = Usuario.builder().id(3L).email("juan@example.com").rol(RolUsuario.JUGADOR).build();
        LiquidacionRequest request = new LiquidacionRequest(3L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(jugador));

        assertThrows(DatosInvalidosException.class, () -> liquidacionService.liquidar(request, "admin@example.com"));
    }

    @Test
    void liquidar_ShouldThrowDatosInvalidos_WhenPeriodoSeSuperponeConOtraLiquidacion() {
        LiquidacionRequest request = new LiquidacionRequest(1L, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(recepcionistaMock));
        when(liquidacionSueldoRepository.existsByEmpleadoIdAndPeriodoDesdeLessThanEqualAndPeriodoHastaGreaterThanEqual(
                1L, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 7, 15))).thenReturn(true);

        assertThrows(DatosInvalidosException.class, () -> liquidacionService.liquidar(request, "admin@example.com"));
        verify(liquidacionSueldoRepository, never()).save(any());
    }

    @Test
    void liquidar_ShouldConsolidateSessionsBankAndCommissions() {
        LiquidacionRequest request = new LiquidacionRequest(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(recepcionistaMock));
        when(usuarioRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminMock));

        SesionTrabajo sesion = SesionTrabajo.builder().id(1L).empleado(recepcionistaMock).minutosTrabajados(480).estado(EstadoSesionTrabajo.CERRADA).build();
        when(sesionTrabajoRepository.findByEmpleadoIdAndEstadoAndInicioBetween(eq(1L), eq(EstadoSesionTrabajo.CERRADA), any(), any()))
                .thenReturn(List.of(sesion));

        MovimientoBancoHoras mov = MovimientoBancoHoras.builder().id(1L).empleado(recepcionistaMock).minutos(45).liquidado(false).build();
        when(movimientoBancoHorasRepository.findByEmpleadoIdAndLiquidadoFalseAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(List.of(mov));

        when(configuracionSistemaService.obtenerEntidad()).thenReturn(
                ConfiguracionSistema.builder().id(1L).comisionesHabilitadas(true).build());

        Comision comision = Comision.builder().id(1L).recepcionista(recepcionistaMock).origen(OrigenComision.CONSUMO)
                .montoComision(new BigDecimal("50.00")).estado(EstadoComision.PENDIENTE).build();
        when(comisionRepository.findByRecepcionistaIdAndEstadoAndCreatedAtBetween(eq(1L), eq(EstadoComision.PENDIENTE), any(), any()))
                .thenReturn(List.of(comision));

        when(bancoHorasRepository.findByEmpleadoId(1L)).thenReturn(Optional.of(
                BancoHoras.builder().id(1L).empleado(recepcionistaMock).saldoMinutos(45).build()));

        LiquidacionSueldo saved = LiquidacionSueldo.builder().id(99L).empleado(recepcionistaMock).build();
        when(liquidacionSueldoRepository.save(any(LiquidacionSueldo.class))).thenReturn(saved);
        when(liquidacionSueldoMapper.toResponse(saved)).thenReturn(
                new LiquidacionResponse(99L, 1L, "recepcion@example.com", request.desde(), request.hasta(),
                        480, 45, new BigDecimal("50.00"), LocalDateTime.now()));

        LiquidacionResponse result = liquidacionService.liquidar(request, "admin@example.com");

        // Snapshot correcto
        ArgumentCaptor<LiquidacionSueldo> captor = ArgumentCaptor.forClass(LiquidacionSueldo.class);
        verify(liquidacionSueldoRepository).save(captor.capture());
        assertEquals(480, captor.getValue().getTotalMinutosTrabajados());
        assertEquals(45, captor.getValue().getTotalMinutosBancoHoras());
        assertEquals(new BigDecimal("50.00"), captor.getValue().getTotalComisiones());

        // Movimiento marcado como liquidado + comisión pagada
        assertTrue(mov.isLiquidado());
        assertEquals(EstadoComision.PAGADA, comision.getEstado());
        assertEquals(saved, comision.getLiquidacion());

        // Se descuenta el banco de horas y se crea el movimiento LIQUIDACION negativo
        verify(bancoHorasRepository).save(any(BancoHoras.class));
        assertNotNull(result);
        assertEquals(99L, result.id());
    }

    @Test
    void liquidar_ShouldNotIncludeCommissions_WhenEmpleadoIsAdmin() {
        LiquidacionRequest request = new LiquidacionRequest(2L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(adminMock));
        when(usuarioRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminMock));
        when(sesionTrabajoRepository.findByEmpleadoIdAndEstadoAndInicioBetween(anyLong(), any(), any(), any()))
                .thenReturn(List.of());
        when(movimientoBancoHorasRepository.findByEmpleadoIdAndLiquidadoFalseAndCreatedAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(configuracionSistemaService.obtenerEntidad()).thenReturn(
                ConfiguracionSistema.builder().id(1L).comisionesHabilitadas(true).build());

        LiquidacionSueldo saved = LiquidacionSueldo.builder().id(100L).empleado(adminMock).build();
        when(liquidacionSueldoRepository.save(any(LiquidacionSueldo.class))).thenReturn(saved);
        when(liquidacionSueldoMapper.toResponse(saved)).thenReturn(
                new LiquidacionResponse(100L, 2L, "admin@example.com", request.desde(), request.hasta(),
                        0, 0, BigDecimal.ZERO, LocalDateTime.now()));

        liquidacionService.liquidar(request, "admin@example.com");

        // No se consultan comisiones para un ADMIN
        verify(comisionRepository, never()).findByRecepcionistaIdAndEstadoAndCreatedAtBetween(anyLong(), any(), any(), any());
    }
}
