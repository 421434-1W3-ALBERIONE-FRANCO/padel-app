package com.padel.service.impl;

import com.padel.domain.entity.BancoHoras;
import com.padel.domain.entity.ConfiguracionSistema;
import com.padel.domain.entity.SesionTrabajo;
import com.padel.domain.entity.TurnoEmpleado;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.EstadoSesionTrabajo;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoMovimientoBancoHoras;
import com.padel.dto.response.FichajeResponse;
import com.padel.exception.EstadoInvalidoException;
import com.padel.exception.OperacionNoPermitidaException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.repository.BancoHorasRepository;
import com.padel.repository.MovimientoBancoHorasRepository;
import com.padel.repository.SesionTrabajoRepository;
import com.padel.repository.TurnoEmpleadoRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.security.RedesPermitidas;
import com.padel.service.ConfiguracionSistemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
    @Spy
    private RedesPermitidas redesPermitidas = new RedesPermitidas("192.168.1.0/24");

    @InjectMocks
    private SesionTrabajoServiceImpl sesionTrabajoService;

    private Usuario empleadoMock;

    @BeforeEach
    void setUp() {
        empleadoMock = Usuario.builder().id(1L).email("recepcion@example.com").rol(RolUsuario.RECEPCIONISTA).build();
    }

    @Test
    void ficharEntrada_AbreLaJornadaDesdeLaRedDelClub() {
        when(usuarioRepository.findByEmail("recepcion@example.com")).thenReturn(Optional.of(empleadoMock));
        when(sesionTrabajoRepository.findFirstByEmpleadoIdAndEstado(1L, EstadoSesionTrabajo.ABIERTA))
                .thenReturn(Optional.empty());
        when(sesionTrabajoRepository.save(any(SesionTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        FichajeResponse respuesta = sesionTrabajoService.ficharEntrada("Recepcion@Example.com", "192.168.1.40");

        ArgumentCaptor<SesionTrabajo> captor = ArgumentCaptor.forClass(SesionTrabajo.class);
        verify(sesionTrabajoRepository).save(captor.capture());
        assertEquals(EstadoSesionTrabajo.ABIERTA, captor.getValue().getEstado());
        assertFalse(captor.getValue().isCerradaAutomaticamente());
        assertTrue(respuesta.abierto());
        assertNotNull(respuesta.inicio());
    }

    @Test
    void ficharEntrada_FueraDeLaRedDelClubSeRechaza() {
        when(usuarioRepository.findByEmail("recepcion@example.com")).thenReturn(Optional.of(empleadoMock));

        assertThrows(OperacionNoPermitidaException.class,
                () -> sesionTrabajoService.ficharEntrada("recepcion@example.com", "181.45.10.2"));
        verify(sesionTrabajoRepository, never()).save(any());
    }

    @Test
    void ficharEntrada_DosVecesNoAbreUnaSegundaJornada() {
        when(usuarioRepository.findByEmail("recepcion@example.com")).thenReturn(Optional.of(empleadoMock));
        when(sesionTrabajoRepository.findFirstByEmpleadoIdAndEstado(1L, EstadoSesionTrabajo.ABIERTA))
                .thenReturn(Optional.of(SesionTrabajo.builder().id(1L).inicio(LocalDateTime.now().minusHours(1))
                        .estado(EstadoSesionTrabajo.ABIERTA).build()));

        assertThrows(EstadoInvalidoException.class,
                () -> sesionTrabajoService.ficharEntrada("recepcion@example.com", "192.168.1.40"));
        verify(sesionTrabajoRepository, never()).save(any());
    }

    @Test
    void ficharEntrada_UnJugadorNoFicha() {
        Usuario jugador = Usuario.builder().id(3L).email("jugador@example.com").rol(RolUsuario.JUGADOR).build();
        when(usuarioRepository.findByEmail("jugador@example.com")).thenReturn(Optional.of(jugador));

        assertThrows(ResourceNotFoundException.class,
                () -> sesionTrabajoService.ficharEntrada("jugador@example.com", "192.168.1.40"));
    }

    @Test
    void ficharSalida_CierraLaJornadaAbierta() {
        // El fin usa LocalDateTime.now(): el cálculo de minutos extra se valida de forma determinista
        // en los tests de cerrarSesionesHuerfanas. Acá solo la transición y que no la marca como automática.
        SesionTrabajo abierta = SesionTrabajo.builder()
                .id(5L).empleado(empleadoMock).inicio(LocalDateTime.now().minusHours(2)).estado(EstadoSesionTrabajo.ABIERTA).build();
        when(usuarioRepository.findByEmail("recepcion@example.com")).thenReturn(Optional.of(empleadoMock));
        when(sesionTrabajoRepository.findFirstByEmpleadoIdAndEstado(1L, EstadoSesionTrabajo.ABIERTA))
                .thenReturn(Optional.of(abierta));
        when(sesionTrabajoRepository.save(any(SesionTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        FichajeResponse respuesta = sesionTrabajoService.ficharSalida("recepcion@example.com", "192.168.1.40");

        assertEquals(EstadoSesionTrabajo.CERRADA, abierta.getEstado());
        assertFalse(abierta.isCerradaAutomaticamente());
        assertFalse(respuesta.abierto());
        assertTrue(respuesta.minutosTrabajados() >= 119);
    }

    @Test
    void ficharSalida_SinJornadaAbiertaSeRechaza() {
        when(usuarioRepository.findByEmail("recepcion@example.com")).thenReturn(Optional.of(empleadoMock));
        when(sesionTrabajoRepository.findFirstByEmpleadoIdAndEstado(1L, EstadoSesionTrabajo.ABIERTA))
                .thenReturn(Optional.empty());

        assertThrows(EstadoInvalidoException.class,
                () -> sesionTrabajoService.ficharSalida("recepcion@example.com", "192.168.1.40"));
    }

    @Test
    void fichajeActual_SinJornadaDevuelveCerrado() {
        when(usuarioRepository.findByEmail("recepcion@example.com")).thenReturn(Optional.of(empleadoMock));
        when(sesionTrabajoRepository.findFirstByEmpleadoIdAndEstado(1L, EstadoSesionTrabajo.ABIERTA))
                .thenReturn(Optional.empty());

        FichajeResponse respuesta = sesionTrabajoService.fichajeActual("recepcion@example.com");

        assertFalse(respuesta.abierto());
        assertNull(respuesta.sesionId());
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
