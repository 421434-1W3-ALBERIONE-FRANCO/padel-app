package com.padel.service.impl;

import com.padel.domain.entity.*;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.OrigenReserva;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoSuelo;
import com.padel.dto.request.ReservaRequest;
import com.padel.dto.response.ReservaResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.exception.ReservaNoModificableException;
import com.padel.exception.SlotNoDisponibleException;
import com.padel.mapper.ReservaMapper;
import com.padel.repository.*;
import com.padel.service.NotificacionService;
import com.padel.service.RedisLockService;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservaServiceImplTest {

    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private CanchaRepository canchaRepository;
    @Mock
    private FranjaHorariaRepository franjaHorariaRepository;
    @Mock
    private BloqueoCanchaRepository bloqueoCanchaRepository;
    @Mock
    private RedisLockService redisLockService;
    @Mock
    private NotificacionService notificacionService;
    @Mock
    private ReservaMapper reservaMapper;

    @InjectMocks
    private ReservaServiceImpl reservaService;

    private Usuario usuarioMock;
    private Cancha canchaMock;
    private FranjaHoraria franjaMock;
    private Reserva reservaMock;
    private ReservaRequest requestMock;
    private ReservaResponse responseMock;

    @BeforeEach
    void setUp() {
        usuarioMock = Usuario.builder()
                .id(1L)
                .nombre("Juan")
                .apellido("Perez")
                .email("juan@example.com")
                .telefono("123456789")
                .rol(RolUsuario.JUGADOR)
                .activo(true)
                .build();

        canchaMock = Cancha.builder()
                .id(1L)
                .nombre("Cancha 1")
                .tipoSuelo(TipoSuelo.BLINDEX)
                .techada(true)
                .tieneLuz(true)
                .activa(true)
                .build();

        franjaMock = FranjaHoraria.builder()
                .id(1L)
                .cancha(canchaMock)
                .horaInicio(LocalTime.of(18, 0))
                .horaFin(LocalTime.of(19, 30))
                .duracionMin(90)
                .precioBase(new BigDecimal("1000.00"))
                .precioNocturno(new BigDecimal("1200.00"))
                .diasAplicables("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY")
                .build();

        reservaMock = Reserva.builder()
                .id(1L)
                .usuario(usuarioMock)
                .cancha(canchaMock)
                .franjaHoraria(franjaMock)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(franjaMock.getHoraInicio())
                .horaFin(franjaMock.getHoraFin())
                .precioTotal(new BigDecimal("1200.00"))
                .estadoReserva(EstadoReserva.PENDIENTE_PAGO)
                .origen(OrigenReserva.APP)
                .build();

        requestMock = new ReservaRequest(
                1L,
                1L,
                LocalDate.now().plusDays(1),
                OrigenReserva.APP
        );

        responseMock = new ReservaResponse(
                1L,
                1L,
                "juan@example.com",
                1L,
                "Cancha 1",
                1L,
                LocalDate.now().plusDays(1),
                LocalTime.of(18, 0),
                LocalTime.of(19, 30),
                new BigDecimal("1200.00"),
                EstadoReserva.PENDIENTE_PAGO,
                OrigenReserva.APP,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    void crearReserva_HappyPath() {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(canchaMock));
        when(franjaHorariaRepository.findById(1L)).thenReturn(Optional.of(franjaMock));
        when(redisLockService.acquireLock(eq(1L), any(LocalDate.class), any(LocalTime.class))).thenReturn(true);
        when(bloqueoCanchaRepository.findBlocksForDate(eq(1L), any(LocalDate.class))).thenReturn(Collections.emptyList());
        when(reservaRepository.findActiveReservationsByCanchaAndDate(eq(1L), any(LocalDate.class))).thenReturn(Collections.emptyList());
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reservaMock);
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(responseMock);

        ReservaResponse result = reservaService.crearReserva(requestMock, "juan@example.com");

        assertNotNull(result);
        assertEquals(EstadoReserva.PENDIENTE_PAGO, result.estadoReserva());
        verify(redisLockService).acquireLock(eq(1L), any(LocalDate.class), any(LocalTime.class));
        verify(reservaRepository).save(any(Reserva.class));
    }

    @Test
    void crearReserva_ShouldThrowSlotNoDisponibleException_WhenRedisLockFails() {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(canchaMock));
        when(franjaHorariaRepository.findById(1L)).thenReturn(Optional.of(franjaMock));
        when(redisLockService.acquireLock(eq(1L), any(LocalDate.class), any(LocalTime.class))).thenReturn(false);

        assertThrows(SlotNoDisponibleException.class, () ->
                reservaService.crearReserva(requestMock, "juan@example.com"));

        verify(redisLockService, never()).releaseLock(anyLong(), any(), any());
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void crearReserva_ShouldReleaseLock_WhenBlocked() {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(canchaMock));
        when(franjaHorariaRepository.findById(1L)).thenReturn(Optional.of(franjaMock));
        when(redisLockService.acquireLock(eq(1L), any(LocalDate.class), any(LocalTime.class))).thenReturn(true);

        BloqueoCancha block = BloqueoCancha.builder()
                .cancha(canchaMock)
                .fechaDesde(requestMock.fecha())
                .fechaHasta(requestMock.fecha())
                .horaDesde(LocalTime.of(17, 0))
                .horaHasta(LocalTime.of(19, 0))
                .motivo("Mantenimiento")
                .build();
        when(bloqueoCanchaRepository.findBlocksForDate(eq(1L), any(LocalDate.class))).thenReturn(List.of(block));

        assertThrows(SlotNoDisponibleException.class, () ->
                reservaService.crearReserva(requestMock, "juan@example.com"));

        verify(redisLockService).releaseLock(eq(1L), any(LocalDate.class), any(LocalTime.class));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void crearReserva_ShouldReleaseLock_WhenAlreadyReserved() {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(canchaMock));
        when(franjaHorariaRepository.findById(1L)).thenReturn(Optional.of(franjaMock));
        when(redisLockService.acquireLock(eq(1L), any(LocalDate.class), any(LocalTime.class))).thenReturn(true);
        when(bloqueoCanchaRepository.findBlocksForDate(eq(1L), any(LocalDate.class))).thenReturn(Collections.emptyList());

        Reserva existingReserva = Reserva.builder()
                .cancha(canchaMock)
                .fecha(requestMock.fecha())
                .horaInicio(LocalTime.of(18, 0))
                .horaFin(LocalTime.of(19, 30))
                .estadoReserva(EstadoReserva.CONFIRMADA)
                .build();
        when(reservaRepository.findActiveReservationsByCanchaAndDate(eq(1L), any(LocalDate.class))).thenReturn(List.of(existingReserva));

        assertThrows(SlotNoDisponibleException.class, () ->
                reservaService.crearReserva(requestMock, "juan@example.com"));

        verify(redisLockService).releaseLock(eq(1L), any(LocalDate.class), any(LocalTime.class));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void cancelarReserva_HappyPath_UserCancelsOwn() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reservaMock);
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(responseMock);

        ReservaResponse result = reservaService.cancelarReserva(1L, "juan@example.com", "Me arrepentí");

        assertNotNull(result);
        verify(reservaRepository).save(reservaMock);
        verify(redisLockService).releaseLock(eq(1L), any(LocalDate.class), any(LocalTime.class));
        verify(notificacionService).enviarCancelacion(any(Reserva.class));
    }

    @Test
    void cancelarReserva_ShouldThrowResourceNotFoundException_WhenUserCancelsOtherUserReservation() {
        Usuario otherUsuario = Usuario.builder()
                .id(2L)
                .nombre("Maria")
                .email("maria@example.com")
                .rol(RolUsuario.JUGADOR)
                .build();

        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("maria@example.com")).thenReturn(Optional.of(otherUsuario));

        assertThrows(ResourceNotFoundException.class, () ->
                reservaService.cancelarReserva(1L, "maria@example.com", "Intento cancelar ajeno"));

        verify(reservaRepository, never()).save(any());
    }

    @Test
    void cancelarReserva_HappyPath_AdminCancelsOtherUserReservation() {
        Usuario adminUsuario = Usuario.builder()
                .id(3L)
                .nombre("Admin")
                .email("admin@example.com")
                .rol(RolUsuario.ADMIN)
                .build();

        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUsuario));
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reservaMock);
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(responseMock);

        ReservaResponse result = reservaService.cancelarReserva(1L, "admin@example.com", "Cancelado por admin");

        assertNotNull(result);
        verify(reservaRepository).save(reservaMock);
    }

    @Test
    void cancelarReserva_ShouldThrowReservaNoModificableException_WhenAlreadyCompleted() {
        reservaMock.setEstadoReserva(EstadoReserva.COMPLETADA);

        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));

        assertThrows(ReservaNoModificableException.class, () ->
                reservaService.cancelarReserva(1L, "juan@example.com", "Completed"));

        verify(reservaRepository, never()).save(any());
    }

    @Test
    void limpiarReservasExpiradas_ShouldCancelExpiredReservations() {
        Reserva expired = Reserva.builder()
                .id(10L)
                .cancha(canchaMock)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(10, 0))
                .usuario(usuarioMock)
                .estadoReserva(EstadoReserva.PENDIENTE_PAGO)
                .build();

        when(reservaRepository.findByEstadoReservaAndCreatedAtBefore(eq(EstadoReserva.PENDIENTE_PAGO), any(LocalDateTime.class)))
                .thenReturn(List.of(expired));

        reservaService.limpiarReservasExpiradas();

        assertEquals(EstadoReserva.CANCELADA, expired.getEstadoReserva());
        assertEquals("Expiración del tiempo de pago (15 minutos)", expired.getMotivoCancelacion());
        verify(reservaRepository).save(expired);
        verify(redisLockService).releaseLock(eq(1L), eq(expired.getFecha()), eq(expired.getHoraInicio()));
    }

    @Test
    void confirmarReserva_HappyPath() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reservaMock);
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(responseMock);

        ReservaResponse result = reservaService.confirmarReserva(1L);

        assertNotNull(result);
        verify(reservaRepository).save(reservaMock);
        verify(redisLockService).releaseLock(eq(1L), any(LocalDate.class), any(LocalTime.class));
        verify(notificacionService).enviarConfirmacion(any(Reserva.class));
    }

    @Test
    void confirmarReserva_ShouldReturnResponse_WhenAlreadyConfirmed() {
        reservaMock.setEstadoReserva(EstadoReserva.CONFIRMADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(responseMock);

        ReservaResponse result = reservaService.confirmarReserva(1L);

        assertNotNull(result);
        verify(reservaRepository, never()).save(any());
        verify(redisLockService, never()).releaseLock(anyLong(), any(), any());
    }

    @Test
    void confirmarReserva_ShouldThrowReservaNoModificableException_WhenCancelled() {
        reservaMock.setEstadoReserva(EstadoReserva.CANCELADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));

        assertThrows(ReservaNoModificableException.class, () ->
                reservaService.confirmarReserva(1L));

        verify(reservaRepository, never()).save(any());
    }
}
