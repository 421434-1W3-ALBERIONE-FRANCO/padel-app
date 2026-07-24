package com.padel.service.impl;

import com.padel.domain.entity.BancoHoras;
import com.padel.domain.entity.ConfiguracionSistema;
import com.padel.domain.entity.SesionTrabajo;
import com.padel.domain.entity.TurnoEmpleado;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.EstadoSesionTrabajo;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoMovimientoBancoHoras;
import com.padel.repository.BancoHorasRepository;
import com.padel.repository.MovimientoBancoHorasRepository;
import com.padel.repository.SesionTrabajoRepository;
import com.padel.repository.TurnoEmpleadoRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.ConfiguracionSistemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SesionTrabajoServiceImplTest {

    @Mock
    private SesionTrabajoRepository sesionTrabajoRepository;
    @Mock
    private TurnoEmpleadoRepository turnoEmpleadoRepository;
    @Mock
    private BancoHorasRepository bancoHorasRepository;
    @Mock
    private MovimientoBancoHorasRepository movimientoBancoHorasRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ConfiguracionSistemaService configuracionSistemaService;

    @InjectMocks
    private SesionTrabajoServiceImpl sesionTrabajoService;

    private Usuario empleadoMock;

    @BeforeEach
    void setUp() {
        empleadoMock = Usuario.builder().id(1L).email("recepcion@example.com").rol(RolUsuario.RECEPCIONISTA).build();
    }

    @Test
    void abrirSesion_ShouldNotOpenSecondSession_WhenOneAlreadyOpen() {
        when(sesionTrabajoRepository.findFirstByEmpleadoIdAndEstado(1L, EstadoSesionTrabajo.ABIERTA))
                .thenReturn(Optional.of(SesionTrabajo.builder().id(1L).build()));

        sesionTrabajoService.abrirSesion(empleadoMock);

        verify(sesionTrabajoRepository, never()).save(any());
    }

    @Test
    void abrirSesion_ShouldCreateSession_WhenNoneOpen() {
        when(sesionTrabajoRepository.findFirstByEmpleadoIdAndEstado(1L, EstadoSesionTrabajo.ABIERTA))
                .thenReturn(Optional.empty());

        sesionTrabajoService.abrirSesion(empleadoMock);

        ArgumentCaptor<SesionTrabajo> captor = ArgumentCaptor.forClass(SesionTrabajo.class);
        verify(sesionTrabajoRepository).save(captor.capture());
        assertEquals(EstadoSesionTrabajo.ABIERTA, captor.getValue().getEstado());
    }

    @Test
    void cerrarSesion_ShouldMarkSessionClosed_WhenOpenSessionExists() {
        // cerrarSesion usa LocalDateTime.now() para el fin, por lo que la lógica de minutos extra
        // (que depende de la hora exacta de cierre) se valida de forma determinista en los tests
        // de cerrarSesionesHuerfanas. Acá solo verificamos la transición de estado.
        LocalDateTime inicio = LocalDateTime.of(2026, 7, 20, 8, 0); // lunes
        SesionTrabajo abierta = SesionTrabajo.builder()
                .id(5L).empleado(empleadoMock).inicio(inicio).estado(EstadoSesionTrabajo.ABIERTA).build();

        when(sesionTrabajoRepository.findFirstByEmpleadoIdAndEstado(1L, EstadoSesionTrabajo.ABIERTA))
                .thenReturn(Optional.of(abierta));
        when(sesionTrabajoRepository.save(any(SesionTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        sesionTrabajoService.cerrarSesion(empleadoMock, false);

        assertEquals(EstadoSesionTrabajo.CERRADA, abierta.getEstado());
        verify(sesionTrabajoRepository).save(abierta);
    }

    @Test
    void cerrarSesion_ShouldDoNothing_WhenNoOpenSession() {
        when(sesionTrabajoRepository.findFirstByEmpleadoIdAndEstado(1L, EstadoSesionTrabajo.ABIERTA))
                .thenReturn(Optional.empty());

        sesionTrabajoService.cerrarSesion(empleadoMock, false);

        verify(sesionTrabajoRepository, never()).save(any());
    }

    @Test
    void cerrarSesionesHuerfanas_ShouldCloseAtShiftEndAndCreditExtra() {
        // Sesión abierta a las 07:30 (30 min antes del turno 08:00-14:00). Cierre huérfano usa horaFin=14:00.
        // Extra: 30 min (apertura temprana) + 0 (cierre al horaFin exacto) = 30 min.
        LocalDateTime inicio = LocalDateTime.of(2026, 7, 20, 7, 30); // lunes
        SesionTrabajo abierta = SesionTrabajo.builder()
                .id(6L).empleado(empleadoMock).inicio(inicio).estado(EstadoSesionTrabajo.ABIERTA).build();

        when(sesionTrabajoRepository.findByEstado(EstadoSesionTrabajo.ABIERTA)).thenReturn(java.util.List.of(abierta));
        when(configuracionSistemaService.obtenerEntidad()).thenReturn(
                ConfiguracionSistema.builder().id(1L).horaTopeCierreSesion(LocalTime.of(23, 59)).build());

        TurnoEmpleado turno = TurnoEmpleado.builder()
                .empleado(empleadoMock).diaSemana(DayOfWeek.MONDAY)
                .horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(14, 0)).build();
        when(turnoEmpleadoRepository.findByEmpleadoIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(Optional.of(turno));
        when(sesionTrabajoRepository.save(any(SesionTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bancoHorasRepository.findByEmpleadoId(1L)).thenReturn(Optional.empty());

        sesionTrabajoService.cerrarSesionesHuerfanas();

        assertEquals(EstadoSesionTrabajo.CERRADA, abierta.getEstado());
        assertTrue(abierta.isCerradaAutomaticamente());
        // 07:30 -> 14:00 = 390 minutos trabajados
        assertEquals(390, abierta.getMinutosTrabajados());

        ArgumentCaptor<BancoHoras> bancoCaptor = ArgumentCaptor.forClass(BancoHoras.class);
        verify(bancoHorasRepository).save(bancoCaptor.capture());
        assertEquals(30, bancoCaptor.getValue().getSaldoMinutos());

        ArgumentCaptor<com.padel.domain.entity.MovimientoBancoHoras> movCaptor =
                ArgumentCaptor.forClass(com.padel.domain.entity.MovimientoBancoHoras.class);
        verify(movimientoBancoHorasRepository).save(movCaptor.capture());
        assertEquals(30, movCaptor.getValue().getMinutos());
        assertEquals(TipoMovimientoBancoHoras.ACREDITACION, movCaptor.getValue().getTipo());
    }

    @Test
    void cerrarSesionesHuerfanas_ShouldNotCreditExtra_WhenNoShiftForThatDay() {
        LocalDateTime inicio = LocalDateTime.of(2026, 7, 20, 7, 30);
        SesionTrabajo abierta = SesionTrabajo.builder()
                .id(7L).empleado(empleadoMock).inicio(inicio).estado(EstadoSesionTrabajo.ABIERTA).build();

        when(sesionTrabajoRepository.findByEstado(EstadoSesionTrabajo.ABIERTA)).thenReturn(java.util.List.of(abierta));
        when(configuracionSistemaService.obtenerEntidad()).thenReturn(
                ConfiguracionSistema.builder().id(1L).horaTopeCierreSesion(LocalTime.of(23, 0)).build());
        when(turnoEmpleadoRepository.findByEmpleadoIdAndDiaSemana(anyLong(), any())).thenReturn(Optional.empty());
        when(sesionTrabajoRepository.save(any(SesionTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        sesionTrabajoService.cerrarSesionesHuerfanas();

        assertEquals(EstadoSesionTrabajo.CERRADA, abierta.getEstado());
        // Sin turno ese día no se acredita banco de horas.
        verify(bancoHorasRepository, never()).save(any());
        verify(movimientoBancoHorasRepository, never()).save(any());
    }
}
