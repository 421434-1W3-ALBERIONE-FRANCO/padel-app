package com.padel.service.impl;

import com.padel.config.ReservasProperties;
import com.padel.domain.entity.BloqueoCancha;
import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.OrigenReserva;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoSuelo;
import com.padel.domain.evento.DisponibilidadCambiadaEvent;
import com.padel.domain.evento.OrigenCancelacion;
import com.padel.domain.evento.ReservaCanceladaEvent;
import com.padel.domain.evento.ReservaConfirmadaEvent;
import com.padel.dto.request.ReservaRequest;
import com.padel.dto.response.ReservaResponse;
import com.padel.exception.EstadoInvalidoException;
import com.padel.exception.ReservaNoModificableException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.exception.SlotNoDisponibleException;
import com.padel.mapper.ReservaMapper;
import com.padel.repository.BloqueoCanchaRepository;
import com.padel.repository.CanchaRepository;
import com.padel.repository.FranjaHorariaRepository;
import com.padel.repository.ReservaRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.security.ControlAcceso;
import com.padel.service.NotificacionInAppService;
import com.padel.service.NotificacionService;
import com.padel.service.RedisLockService;
import com.padel.service.ReintegroService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    private NotificacionInAppService notificacionInAppService;
    @Mock
    private ReintegroService reintegroService;
    @Mock
    private ApplicationEventPublisher eventos;
    @Mock
    private ReservaMapper reservaMapper;
    @Spy
    private ReservasProperties reservasProperties = new ReservasProperties();
    @Spy
    private CalculadoraPrecioReserva calculadoraPrecio = new CalculadoraPrecioReserva(new ReservasProperties());
    @Spy
    private ControlAcceso controlAcceso = new ControlAcceso();
    @Spy
    private com.padel.security.LimitadorEnMemoria limitador = new com.padel.security.LimitadorEnMemoria();
    @Spy
    private com.padel.config.LimitesProperties limites = new com.padel.config.LimitesProperties();
    @Spy
    private TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));

    @InjectMocks
    private ReservaServiceImpl reservaService;

    private Usuario jugador;
    private Usuario admin;
    private Cancha cancha;
    private FranjaHoraria franja18;
    private FranjaHoraria franja1930;
    private FranjaHoraria franja2130;
    private Reserva reserva;
    private ReservaResponse respuesta;
    private final LocalDate maniana = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        jugador = Usuario.builder().id(1L).nombre("Juan").apellido("Perez").email("juan@example.com")
                .rol(RolUsuario.JUGADOR).activo(true).build();
        admin = Usuario.builder().id(3L).nombre("Admin").email("admin@example.com").rol(RolUsuario.ADMIN).activo(true).build();
        cancha = Cancha.builder().id(1L).nombre("Cancha 1").tipoSuelo(TipoSuelo.BLINDEX).techada(true).tieneLuz(true)
                .activa(true).duracionesPermitidas("90,120,180").build();
        franja18 = franja(1L, LocalTime.of(18, 0), LocalTime.of(19, 30));
        franja1930 = franja(2L, LocalTime.of(19, 30), LocalTime.of(21, 0));
        franja2130 = franja(3L, LocalTime.of(21, 30), LocalTime.of(23, 0));

        lenient().when(franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(1L))
                .thenReturn(List.of(franja18, franja1930, franja2130));
        lenient().when(redisLockService.acquireLock(eq(1L), any(LocalDate.class), any(LocalTime.class))).thenReturn(true);
        lenient().when(reintegroService.reintegrarCancelacion(any(), any())).thenReturn("");

        reserva = Reserva.builder().id(1L).usuario(jugador).cancha(cancha).franjaHoraria(franja18).fecha(maniana)
                .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30)).precioTotal(new BigDecimal("1200.00"))
                .origen(OrigenReserva.APP).build();
        respuesta = new ReservaResponse(1L, 1L, "juan@example.com", 1L, "Cancha 1", 1L, maniana,
                LocalTime.of(18, 0), LocalTime.of(19, 30), new BigDecimal("1200.00"), EstadoReserva.PENDIENTE_PAGO,
                OrigenReserva.APP, null, LocalDateTime.now(), LocalDateTime.now());
    }

    private FranjaHoraria franja(Long id, LocalTime inicio, LocalTime fin) {
        return FranjaHoraria.builder().id(id).cancha(cancha).horaInicio(inicio).horaFin(fin)
                .duracionMin((int) java.time.Duration.between(inicio, fin).toMinutes())
                .precioBase(new BigDecimal("1000.00")).precioNocturno(new BigDecimal("1200.00"))
                .diasAplicables("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY").build();
    }

    private void prepararAlta(FranjaHoraria franjaElegida) {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));
        when(canchaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cancha));
        when(franjaHorariaRepository.findById(franjaElegida.getId())).thenReturn(Optional.of(franjaElegida));
    }

    // ---------- crearReserva ----------

    @Test
    void crearReserva_HappyPath_BloqueaLaCanchaYPublicaElCambioDeDisponibilidad() {
        prepararAlta(franja18);
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(respuesta);

        ReservaResponse result = reservaService.crearReserva(
                new ReservaRequest(1L, 1L, maniana, OrigenReserva.APP, null, null), "juan@example.com");

        assertNotNull(result);
        verify(canchaRepository).findByIdForUpdate(1L);
        ArgumentCaptor<Reserva> captor = ArgumentCaptor.forClass(Reserva.class);
        verify(reservaRepository).saveAndFlush(captor.capture());
        assertEquals(new BigDecimal("1200.00"), captor.getValue().getPrecioTotal());
        assertEquals(LocalTime.of(19, 30), captor.getValue().getHoraFin());
        verify(eventos).publishEvent(new DisponibilidadCambiadaEvent(1L));
    }

    @Test
    void crearReserva_DeberiaForzarOrigenAppParaJugadorSinImportarLoEnviado() {
        prepararAlta(franja18);
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(respuesta);

        reservaService.crearReserva(new ReservaRequest(1L, 1L, maniana, OrigenReserva.RECEPCION, null, null), "juan@example.com");

        ArgumentCaptor<Reserva> captor = ArgumentCaptor.forClass(Reserva.class);
        verify(reservaRepository).saveAndFlush(captor.capture());
        assertEquals(OrigenReserva.APP, captor.getValue().getOrigen());
    }

    @Test
    void crearReserva_PrecioProrrateadoCruzandoFranjas() {
        prepararAlta(franja18);
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(respuesta);

        reservaService.crearReserva(new ReservaRequest(1L, 1L, maniana, OrigenReserva.APP, 180, null), "juan@example.com");

        // 18:00-21:00: franja 18:00 completa (1200) + franja 19:30 completa (1200), ambas nocturnas.
        ArgumentCaptor<Reserva> captor = ArgumentCaptor.forClass(Reserva.class);
        verify(reservaRepository).saveAndFlush(captor.capture());
        assertEquals(new BigDecimal("2400.00"), captor.getValue().getPrecioTotal());
    }

    @Test
    void crearReserva_RechazaUnaDuracionQuePasaLaMedianoche() {
        // 21:30 + 180 min = 00:30: antes salía a $0 y sin controlar superposición.
        prepararAlta(franja2130);

        SlotNoDisponibleException ex = assertThrows(SlotNoDisponibleException.class, () -> reservaService.crearReserva(
                new ReservaRequest(1L, 3L, maniana, OrigenReserva.APP, 180, null), "juan@example.com"));

        assertTrue(ex.getMessage().contains("excede el horario"));
        verify(reservaRepository, never()).saveAndFlush(any());
        verify(redisLockService, never()).acquireLock(anyLong(), any(), any());
    }

    @Test
    void crearReserva_RechazaUnaDuracionQueExcedeElHorarioDeApertura() {
        // 21:30 + 120 = 23:30, pero la última franja termina 23:00.
        prepararAlta(franja2130);

        assertThrows(SlotNoDisponibleException.class, () -> reservaService.crearReserva(
                new ReservaRequest(1L, 3L, maniana, OrigenReserva.APP, 120, null), "juan@example.com"));
        verify(reservaRepository, never()).saveAndFlush(any());
    }

    @Test
    void crearReserva_RechazaUnaDuracionNoPermitidaParaLaCancha() {
        prepararAlta(franja18);

        assertThrows(SlotNoDisponibleException.class, () -> reservaService.crearReserva(
                new ReservaRequest(1L, 1L, maniana, OrigenReserva.APP, 60, null), "juan@example.com"));
    }

    @Test
    void crearReserva_LimitaLasReservasPendientesDeUnJugador() {
        prepararAlta(franja18);
        when(reservaRepository.countByUsuarioIdAndEstadoReserva(1L, EstadoReserva.PENDIENTE_PAGO)).thenReturn(2L);

        EstadoInvalidoException ex = assertThrows(EstadoInvalidoException.class, () -> reservaService.crearReserva(
                new ReservaRequest(1L, 1L, maniana, OrigenReserva.APP, null, null), "juan@example.com"));

        assertTrue(ex.getMessage().contains("pendientes de pago"));
        verify(reservaRepository, never()).saveAndFlush(any());
    }

    @Test
    void crearReserva_UnJugadorNoPuedeReservarEnRafaga() {
        limites.getReservasPorJugador().setMaximo(2);
        prepararAlta(franja18);
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(respuesta);
        ReservaRequest request = new ReservaRequest(1L, 1L, maniana, OrigenReserva.APP, null, null);

        reservaService.crearReserva(request, "juan@example.com");
        reservaService.crearReserva(request, "juan@example.com");

        assertThrows(com.padel.exception.DemasiadosIntentosException.class, () -> reservaService.crearReserva(request, "juan@example.com"));
    }

    @Test
    void crearReserva_ElStaffNoTieneLimiteDePendientesYReservaParaUnCliente() {
        Usuario recepcion = Usuario.builder().id(4L).email("recepcion@example.com").rol(RolUsuario.RECEPCIONISTA).activo(true).build();
        when(usuarioRepository.findByEmail("recepcion@example.com")).thenReturn(Optional.of(recepcion));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(jugador));
        when(canchaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cancha));
        when(franjaHorariaRepository.findById(1L)).thenReturn(Optional.of(franja18));
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(respuesta);

        reservaService.crearReserva(new ReservaRequest(1L, 1L, maniana, OrigenReserva.RECEPCION, null, 1L), "recepcion@example.com");

        verify(reservaRepository, never()).countByUsuarioIdAndEstadoReserva(anyLong(), any());
        ArgumentCaptor<Reserva> captor = ArgumentCaptor.forClass(Reserva.class);
        verify(reservaRepository).saveAndFlush(captor.capture());
        assertEquals(jugador, captor.getValue().getUsuario());
        assertEquals(OrigenReserva.RECEPCION, captor.getValue().getOrigen());
    }

    @Test
    void crearReserva_ShouldThrowSlotNoDisponibleException_WhenRedisLockFails() {
        prepararAlta(franja18);
        when(redisLockService.acquireLock(eq(1L), any(LocalDate.class), any(LocalTime.class))).thenReturn(false);

        assertThrows(SlotNoDisponibleException.class, () -> reservaService.crearReserva(
                new ReservaRequest(1L, 1L, maniana, OrigenReserva.APP, null, null), "juan@example.com"));

        verify(redisLockService, never()).releaseLock(anyLong(), any(), any());
        verify(reservaRepository, never()).saveAndFlush(any());
    }

    @Test
    void crearReserva_ShouldReleaseLock_WhenBlocked() {
        prepararAlta(franja18);
        BloqueoCancha bloqueo = BloqueoCancha.builder().cancha(cancha).fechaDesde(maniana).fechaHasta(maniana)
                .horaDesde(LocalTime.of(17, 0)).horaHasta(LocalTime.of(19, 0)).motivo("Mantenimiento").build();
        when(bloqueoCanchaRepository.findBlocksForDate(eq(1L), any(LocalDate.class))).thenReturn(List.of(bloqueo));

        assertThrows(SlotNoDisponibleException.class, () -> reservaService.crearReserva(
                new ReservaRequest(1L, 1L, maniana, OrigenReserva.APP, null, null), "juan@example.com"));

        verify(redisLockService).releaseLock(eq(1L), any(LocalDate.class), any(LocalTime.class));
        verify(reservaRepository, never()).saveAndFlush(any());
    }

    @Test
    void crearReserva_ShouldThrowSlotNoDisponible_WhenOverlapsPendingReservation() {
        // Reserva de 120' de otro usuario (18:00-20:00) todavía pendiente de pago: la franja de las
        // 19:30 no se puede volver a vender aunque el lock de Redis sea de otra hora.
        Reserva pendienteLarga = Reserva.builder().id(9L).cancha(cancha).fecha(maniana)
                .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(20, 0)).build();
        prepararAlta(franja1930);
        when(reservaRepository.findActiveReservationsByCanchaAndDate(eq(1L), any(LocalDate.class))).thenReturn(List.of(pendienteLarga));

        assertThrows(SlotNoDisponibleException.class, () -> reservaService.crearReserva(
                new ReservaRequest(1L, 2L, maniana, OrigenReserva.APP, null, null), "juan@example.com"));

        verify(reservaRepository, never()).saveAndFlush(any());
        verify(redisLockService).releaseLock(eq(1L), any(LocalDate.class), eq(LocalTime.of(19, 30)));
    }

    @Test
    void crearReserva_LaRestriccionDeLaBaseSeTraduceEnTurnoNoDisponible() {
        prepararAlta(franja18);
        when(reservaRepository.saveAndFlush(any(Reserva.class)))
                .thenThrow(new DataIntegrityViolationException("excl_reservas_superpuestas"));

        assertThrows(SlotNoDisponibleException.class, () -> reservaService.crearReserva(
                new ReservaRequest(1L, 1L, maniana, OrigenReserva.APP, null, null), "juan@example.com"));
        verify(redisLockService).releaseLock(eq(1L), any(LocalDate.class), any(LocalTime.class));
    }

    @Test
    void crearReserva_FechaPasadaNoSePuedeReservar() {
        prepararAlta(franja18);

        assertThrows(SlotNoDisponibleException.class, () -> reservaService.crearReserva(
                new ReservaRequest(1L, 1L, LocalDate.now().minusDays(1), OrigenReserva.APP, null, null), "juan@example.com"));
    }

    // ---------- cancelarReserva ----------

    @Test
    void cancelarReserva_HappyPath_UserCancelsOwn_PublicaElEventoConLaPoliticaDeReintegro() {
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reserva);
        when(reintegroService.reintegrarCancelacion(reserva, OrigenCancelacion.JUGADOR)).thenReturn(" Te devolvemos $ 1.200,00.");
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(respuesta);

        reservaService.cancelarReserva(1L, "juan@example.com", "Me arrepentí");

        assertEquals(EstadoReserva.CANCELADA, reserva.getEstadoReserva());
        verify(redisLockService).releaseLock(eq(1L), any(LocalDate.class), any(LocalTime.class));
        verify(eventos).publishEvent(new ReservaCanceladaEvent(reserva, OrigenCancelacion.JUGADOR, " Te devolvemos $ 1.200,00."));
        verify(eventos).publishEvent(new DisponibilidadCambiadaEvent(1L));
    }

    @Test
    void cancelarReserva_ShouldThrowResourceNotFoundException_WhenUserCancelsOtherUserReservation() {
        Usuario otro = Usuario.builder().id(2L).email("maria@example.com").rol(RolUsuario.JUGADOR).build();
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("maria@example.com")).thenReturn(Optional.of(otro));

        assertThrows(ResourceNotFoundException.class, () -> reservaService.cancelarReserva(1L, "maria@example.com", "Ajena"));

        verify(reservaRepository, never()).save(any());
    }

    @Test
    void cancelarReserva_EmailEnMayusculasSeNormalizaAntesDeBuscar() {
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reserva);
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(respuesta);

        reservaService.cancelarReserva(1L, "  JUAN@Example.com ", null);

        verify(usuarioRepository).findByEmail("juan@example.com");
    }

    @Test
    void cancelarReserva_ElStaffCancelaComoClub() {
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reserva);
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(respuesta);

        reservaService.cancelarReserva(1L, "admin@example.com", null);

        assertEquals("Cancelada por el club", reserva.getMotivoCancelacion());
        verify(reintegroService).reintegrarCancelacion(reserva, OrigenCancelacion.CLUB);
    }

    @Test
    void cancelarReserva_ShouldThrowReservaNoModificableException_WhenAlreadyCompleted() {
        ReflectionTestUtils.setField(reserva, "estadoReserva", EstadoReserva.COMPLETADA);
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));

        assertThrows(ReservaNoModificableException.class, () -> reservaService.cancelarReserva(1L, "juan@example.com", "Completed"));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void cancelarReserva_ShouldThrowReservaNoModificableException_WhenNoShow() {
        ReflectionTestUtils.setField(reserva, "estadoReserva", EstadoReserva.NO_SHOW);
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));

        assertThrows(ReservaNoModificableException.class, () -> reservaService.cancelarReserva(1L, "juan@example.com", "No vine"));
    }

    @Test
    void cancelarReserva_JugadorNoPuedeCancelarUnTurnoQueYaPaso() {
        ReflectionTestUtils.setField(reserva, "estadoReserva", EstadoReserva.CONFIRMADA);
        reserva.setFecha(LocalDate.now().minusDays(1));
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));

        assertThrows(ReservaNoModificableException.class, () -> reservaService.cancelarReserva(1L, "juan@example.com", "Ya lo jugué"));
        verify(reservaRepository, never()).save(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void cancelarReserva_StaffPuedeCancelarUnTurnoPasado() {
        Usuario recepcion = Usuario.builder().id(4L).email("recepcion@example.com").rol(RolUsuario.RECEPCIONISTA).build();
        ReflectionTestUtils.setField(reserva, "estadoReserva", EstadoReserva.CONFIRMADA);
        reserva.setFecha(LocalDate.now().minusDays(1));
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("recepcion@example.com")).thenReturn(Optional.of(recepcion));
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reserva);
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(respuesta);

        reservaService.cancelarReserva(1L, "recepcion@example.com", "Corrección de agenda");

        assertEquals(EstadoReserva.CANCELADA, reserva.getEstadoReserva());
        assertEquals("Corrección de agenda", reserva.getMotivoCancelacion());
    }

    @Test
    void cancelarReserva_SinMotivoUsaElMotivoPorDefecto() {
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reserva);
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(respuesta);

        reservaService.cancelarReserva(1L, "juan@example.com", "  ");

        assertEquals("Cancelada por el usuario", reserva.getMotivoCancelacion());
    }

    @Test
    void cancelarPorClub_CancelaConReintegroDelClub() {
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reserva);

        reservaService.cancelarPorClub(1L, "Cancha no disponible: lluvia");

        assertEquals(EstadoReserva.CANCELADA, reserva.getEstadoReserva());
        verify(reintegroService).reintegrarCancelacion(reserva, OrigenCancelacion.CLUB);
    }

    // ---------- vencimiento, finalización y recordatorios ----------

    @Test
    void limpiarReservasExpiradas_CadaUnaEnSuTransaccionYSinFrenarAlResto() {
        Reserva vencida = Reserva.builder().id(10L).cancha(cancha).usuario(jugador).fecha(LocalDate.now())
                .horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(11, 30)).build();
        Reserva conflicto = Reserva.builder().id(11L).cancha(cancha).usuario(jugador).fecha(LocalDate.now())
                .horaInicio(LocalTime.of(12, 0)).horaFin(LocalTime.of(13, 30)).build();
        when(reservaRepository.findByEstadoReservaAndCreatedAtBefore(eq(EstadoReserva.PENDIENTE_PAGO), any(LocalDateTime.class)))
                .thenReturn(List.of(conflicto, vencida));
        // La primera falla (p. ej. el webhook la confirmó al mismo tiempo): la segunda se procesa igual.
        when(reservaRepository.findByIdForUpdate(11L)).thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Reserva.class, 11L));
        when(reservaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(vencida));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        reservaService.limpiarReservasExpiradas();

        assertEquals(EstadoReserva.CANCELADA, vencida.getEstadoReserva());
        assertEquals("Expiración del tiempo de pago (15 minutos)", vencida.getMotivoCancelacion());
        verify(redisLockService).releaseLock(eq(1L), eq(vencida.getFecha()), eq(vencida.getHoraInicio()));
        verify(eventos).publishEvent(new ReservaCanceladaEvent(vencida, OrigenCancelacion.FALTA_DE_PAGO, ""));
    }

    @Test
    void limpiarReservasExpiradas_NoTocaUnaReservaQueYaSeConfirmo() {
        Reserva confirmada = Reserva.builder().id(12L).cancha(cancha).usuario(jugador).fecha(LocalDate.now())
                .horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(11, 30)).estadoReserva(EstadoReserva.CONFIRMADA).build();
        when(reservaRepository.findByEstadoReservaAndCreatedAtBefore(eq(EstadoReserva.PENDIENTE_PAGO), any(LocalDateTime.class)))
                .thenReturn(List.of(confirmada));
        when(reservaRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(confirmada));

        reservaService.limpiarReservasExpiradas();

        assertEquals(EstadoReserva.CONFIRMADA, confirmada.getEstadoReserva());
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void completarReservasFinalizadas_PasaACompletadaLasConfirmadasCuyoTurnoTermino() {
        Reserva deAyer = Reserva.builder().id(20L).usuario(jugador).cancha(cancha).fecha(LocalDate.now().minusDays(1))
                .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30)).estadoReserva(EstadoReserva.CONFIRMADA).build();
        Reserva deLaSemanaPasada = Reserva.builder().id(21L).usuario(jugador).cancha(cancha).fecha(LocalDate.now().minusDays(7))
                .horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(11, 30)).estadoReserva(EstadoReserva.CONFIRMADA).build();
        when(reservaRepository.findConfirmadasFinalizadas(any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(List.of(deAyer, deLaSemanaPasada));

        LocalDateTime antes = LocalDateTime.now();
        reservaService.completarReservasFinalizadas();
        LocalDateTime despues = LocalDateTime.now();

        assertEquals(EstadoReserva.COMPLETADA, deAyer.getEstadoReserva());
        assertEquals(EstadoReserva.COMPLETADA, deLaSemanaPasada.getEstadoReserva());
        verify(reservaRepository).saveAll(List.of(deAyer, deLaSemanaPasada));
        verifyNoInteractions(redisLockService, notificacionService, notificacionInAppService);

        ArgumentCaptor<LocalDate> fecha = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalTime> hora = ArgumentCaptor.forClass(LocalTime.class);
        verify(reservaRepository).findConfirmadasFinalizadas(fecha.capture(), hora.capture());
        LocalDateTime corte = LocalDateTime.of(fecha.getValue(), hora.getValue());
        assertFalse(corte.isBefore(antes));
        assertFalse(corte.isAfter(despues));
    }

    @Test
    void completarReservasFinalizadas_SinTurnosTerminadosNoGuardaNada() {
        when(reservaRepository.findConfirmadasFinalizadas(any(LocalDate.class), any(LocalTime.class))).thenReturn(List.of());

        reservaService.completarReservasFinalizadas();

        verify(reservaRepository, never()).saveAll(any());
    }

    @Test
    void obtenerPorFecha_DevuelveLasReservasDelDiaSinRecorrerElHistorial() {
        when(reservaRepository.findDelDia(maniana)).thenReturn(List.of(reserva));
        when(reservaMapper.toResponse(reserva)).thenReturn(respuesta);

        assertEquals(List.of(respuesta), reservaService.obtenerPorFecha(maniana));
        verify(reservaRepository, never()).findAll();
    }

    // ---------- confirmarReserva ----------

    @Test
    void confirmarReserva_HappyPath_PublicaLaConfirmacion() {
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reserva);
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(respuesta);

        assertNotNull(reservaService.confirmarReserva(1L));

        assertEquals(EstadoReserva.CONFIRMADA, reserva.getEstadoReserva());
        verify(redisLockService).releaseLock(eq(1L), any(LocalDate.class), any(LocalTime.class));
        verify(eventos).publishEvent(new ReservaConfirmadaEvent(reserva));
    }

    @Test
    void confirmarReserva_ShouldReturnResponse_WhenAlreadyConfirmed() {
        ReflectionTestUtils.setField(reserva, "estadoReserva", EstadoReserva.CONFIRMADA);
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(respuesta);

        assertNotNull(reservaService.confirmarReserva(1L));
        verify(reservaRepository, never()).save(any());
        verify(redisLockService, never()).releaseLock(anyLong(), any(), any());
    }

    @Test
    void confirmarReserva_ShouldThrowReservaNoModificableException_WhenCancelled() {
        ReflectionTestUtils.setField(reserva, "estadoReserva", EstadoReserva.CANCELADA);
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));

        assertThrows(ReservaNoModificableException.class, () -> reservaService.confirmarReserva(1L));
        verify(reservaRepository, never()).save(any());
    }

    // ---------- recordatorios ----------

    @Test
    void notificarReservasDeHoy_AvisaInAppYPorWhatsAppYMarcaElRecordatorio() {
        Reserva deHoy = Reserva.builder().id(5L).usuario(jugador).cancha(cancha).fecha(LocalDate.now())
                .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30)).estadoReserva(EstadoReserva.CONFIRMADA)
                .recordatorioEnviado(false).build();
        when(reservaRepository.findByFechaAndEstadoReservaAndRecordatorioEnviadoFalse(any(LocalDate.class), eq(EstadoReserva.CONFIRMADA)))
                .thenReturn(List.of(deHoy));

        reservaService.notificarReservasDeHoy();

        assertTrue(deHoy.isRecordatorioEnviado());
        verify(notificacionInAppService).crear(eq(jugador), any(), any(), any(), eq(5L));
        verify(notificacionService).enviarRecordatorio(deHoy);
        verify(reservaRepository).save(deHoy);
    }

    @Test
    void notificarReservasDeHoy_ShouldDoNothing_WhenNoReservationsFound() {
        when(reservaRepository.findByFechaAndEstadoReservaAndRecordatorioEnviadoFalse(any(LocalDate.class), eq(EstadoReserva.CONFIRMADA)))
                .thenReturn(List.of());

        reservaService.notificarReservasDeHoy();

        verify(notificacionInAppService, never()).crear(any(), any(), any(), any(), any());
        verify(reservaRepository, never()).save(any());
    }
}
