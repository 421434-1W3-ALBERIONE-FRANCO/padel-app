package com.padel.service.impl;

import com.padel.config.ReservasProperties;
import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.ConceptoPago;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.MetodoPago;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.dto.response.PagoResponse;
import com.padel.dto.response.PreferenciaResponse;
import com.padel.exception.DatosInvalidosException;
import com.padel.exception.EstadoInvalidoException;
import com.padel.exception.ReservaNoModificableException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.integracion.pagos.PasarelaPagos;
import com.padel.integracion.pagos.PasarelaPagos.NuevaPreferencia;
import com.padel.integracion.pagos.PasarelaPagos.PagoExterno;
import com.padel.integracion.pagos.PasarelaPagos.PreferenciaCreada;
import com.padel.mapper.PagoMapper;
import com.padel.repository.PagoRepository;
import com.padel.repository.ReservaRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.security.ControlAcceso;
import com.padel.service.ConsumoService;
import com.padel.service.EmailService;
import com.padel.service.NotificacionInAppService;
import com.padel.service.ReintegroService;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceImplTest {

    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ReservaService reservaService;
    @Mock
    private ConsumoService consumoService;
    @Mock
    private ReintegroService reintegroService;
    @Mock
    private PasarelaPagos pasarelaPagos;
    @Mock
    private PagoMapper pagoMapper;
    @Mock
    private EmailService emailService;
    @Mock
    private NotificacionInAppService notificacionInAppService;
    @Spy
    private ControlAcceso controlAcceso = new ControlAcceso();
    @Spy
    private ReservasProperties reservasProperties = new ReservasProperties();

    @InjectMocks
    private PagoServiceImpl pagoService;

    private Usuario jugador;
    private Reserva reserva;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pagoService, "frontendUrl", "https://club.example.com/");
        jugador = Usuario.builder().id(1L).nombre("Juan").apellido("Perez").email("juan@example.com").rol(RolUsuario.JUGADOR).activo(true).build();
        Cancha cancha = Cancha.builder().id(1L).nombre("Cancha 1").build();
        reserva = Reserva.builder().id(1L).usuario(jugador).cancha(cancha).fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30)).precioTotal(new BigDecimal("1500.00")).build();
        reserva.setCreatedAt(LocalDateTime.now());
    }

    private PagoExterno pagoMp(String estado, String referencia, String monto, String moneda) {
        return new PagoExterno("123", estado, referencia, monto == null ? null : new BigDecimal(monto), moneda);
    }

    // ---------- crearPreferencia ----------

    @Test
    void crearPreferencia_HappyPath_VenceJuntoConLaReserva() {
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));
        when(pasarelaPagos.crearPreferencia(any())).thenReturn(new PreferenciaCreada("pref-1", "https://mp/checkout"));
        when(pagoRepository.findByReservaIdAndEstadoAndConcepto(1L, EstadoPago.PENDIENTE, ConceptoPago.TURNO)).thenReturn(Optional.empty());

        PreferenciaResponse response = pagoService.crearPreferencia(1L, "juan@example.com");

        assertEquals("pref-1", response.preferenceId());
        assertEquals("https://mp/checkout", response.initPoint());
        ArgumentCaptor<NuevaPreferencia> captor = ArgumentCaptor.forClass(NuevaPreferencia.class);
        verify(pasarelaPagos).crearPreferencia(captor.capture());
        assertEquals(new BigDecimal("1500.00"), captor.getValue().precio());
        assertEquals("1", captor.getValue().referenciaExterna());
        assertEquals("https://club.example.com/reservas/mis-reservas?status=success", captor.getValue().urlExito());
        assertNotNull(captor.getValue().venceEl());
        ArgumentCaptor<Pago> pago = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(pago.capture());
        assertEquals("pref-1", pago.getValue().getMpPreferenceId());
        assertEquals(ConceptoPago.TURNO, pago.getValue().getConcepto());
    }

    @Test
    void crearPreferencia_DeberiaLanzarReservaNoModificable_CuandoNoEstaPendienteDePago() {
        ReflectionTestUtils.setField(reserva, "estadoReserva", EstadoReserva.CANCELADA);
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));

        assertThrows(ReservaNoModificableException.class, () -> pagoService.crearPreferencia(1L, "juan@example.com"));
        verifyNoInteractions(pasarelaPagos);
    }

    @Test
    void crearPreferencia_RechazaUnTurnoSinImporte() {
        reserva.setPrecioTotal(BigDecimal.ZERO);
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));

        assertThrows(DatosInvalidosException.class, () -> pagoService.crearPreferencia(1L, "juan@example.com"));
        verifyNoInteractions(pasarelaPagos);
    }

    @Test
    void crearPreferencia_RechazaUnaReservaCuyoPlazoDePagoYaVencio() {
        reserva.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));

        assertThrows(ReservaNoModificableException.class, () -> pagoService.crearPreferencia(1L, "juan@example.com"));
        verifyNoInteractions(pasarelaPagos);
    }

    @Test
    void crearPreferencia_ShouldThrowResourceNotFoundException_WhenUserNotOwner() {
        Usuario otro = Usuario.builder().id(2L).email("otro@example.com").rol(RolUsuario.JUGADOR).build();
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("otro@example.com")).thenReturn(Optional.of(otro));

        assertThrows(ResourceNotFoundException.class, () -> pagoService.crearPreferencia(1L, "otro@example.com"));
    }

    // ---------- webhook ----------

    @Test
    void procesarWebhook_ApprovedPayment_FirstTime_ConfirmaLaReserva() {
        Pago pendiente = Pago.builder().id(5L).reserva(reserva).usuario(jugador).monto(new BigDecimal("1500.00"))
                .metodo(MetodoPago.MERCADOPAGO).estado(EstadoPago.PENDIENTE).build();
        when(pagoRepository.findByMpPaymentId("123")).thenReturn(Optional.empty());
        when(pasarelaPagos.consultarPago("123")).thenReturn(pagoMp("approved", "1", "1500.00", "ARS"));
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReservaId(1L)).thenReturn(List.of(pendiente));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        pagoService.procesarWebhook("payment", "123");

        assertEquals(EstadoPago.APROBADO, pendiente.getEstado());
        assertEquals("123", pendiente.getMpPaymentId());
        verify(emailService).enviarComprobantePago(pendiente);
        verify(reservaService).confirmarReserva(1L);
    }

    @Test
    void procesarWebhook_MontoMenorAlEsperado_NoConfirmaYAvisaAlAdmin() {
        Usuario admin = Usuario.builder().id(9L).rol(RolUsuario.ADMIN).activo(true).build();
        Pago pendiente = Pago.builder().id(5L).reserva(reserva).usuario(jugador).monto(new BigDecimal("1500.00"))
                .metodo(MetodoPago.MERCADOPAGO).estado(EstadoPago.PENDIENTE).build();
        when(pagoRepository.findByMpPaymentId("123")).thenReturn(Optional.empty());
        when(pasarelaPagos.consultarPago("123")).thenReturn(pagoMp("approved", "1", "10.00", "ARS"));
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReservaId(1L)).thenReturn(List.of(pendiente));
        when(usuarioRepository.findByRol(RolUsuario.ADMIN)).thenReturn(List.of(admin));

        pagoService.procesarWebhook("payment", "123");

        assertEquals(EstadoPago.PENDIENTE, pendiente.getEstado());
        verify(reservaService, never()).confirmarReserva(any());
        verify(notificacionInAppService).crear(eq(admin), eq(TipoNotificacion.PAGO_EN_REVISION), anyString(), anyString(), eq(1L));
    }

    @Test
    void procesarWebhook_MonedaDistinta_NoConfirma() {
        Pago pendiente = Pago.builder().id(5L).reserva(reserva).usuario(jugador).monto(new BigDecimal("1500.00"))
                .metodo(MetodoPago.MERCADOPAGO).estado(EstadoPago.PENDIENTE).build();
        when(pagoRepository.findByMpPaymentId("123")).thenReturn(Optional.empty());
        when(pasarelaPagos.consultarPago("123")).thenReturn(pagoMp("approved", "1", "1500.00", "USD"));
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReservaId(1L)).thenReturn(List.of(pendiente));

        pagoService.procesarWebhook("payment", "123");

        verify(reservaService, never()).confirmarReserva(any());
    }

    @Test
    void procesarWebhook_ApprovedPayment_Idempotent() {
        Pago aprobado = Pago.builder().id(5L).estado(EstadoPago.APROBADO).mpPaymentId("123").build();
        when(pagoRepository.findByMpPaymentId("123")).thenReturn(Optional.of(aprobado));

        pagoService.procesarWebhook("payment", "123");

        verifyNoInteractions(pasarelaPagos, reservaService);
    }

    @Test
    void procesarWebhook_PagoAprobadoDeUnaReservaVencida_SeReintegra() {
        ReflectionTestUtils.setField(reserva, "estadoReserva", EstadoReserva.CANCELADA);
        when(pagoRepository.findByMpPaymentId("123")).thenReturn(Optional.empty());
        when(pasarelaPagos.consultarPago("123")).thenReturn(pagoMp("approved", "1", "1500.00", "ARS"));
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReservaId(1L)).thenReturn(List.of());
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        pagoService.procesarWebhook("payment", "123");

        verify(reservaService, never()).confirmarReserva(any());
        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(reintegroService).reintegrarPagoTardio(captor.capture());
        assertEquals(EstadoPago.APROBADO, captor.getValue().getEstado());
    }

    @Test
    void procesarWebhook_PagoSinReferenciaDeReserva_SeIgnora() {
        when(pagoRepository.findByMpPaymentId("123")).thenReturn(Optional.empty());
        when(pasarelaPagos.consultarPago("123")).thenReturn(pagoMp("approved", null, "1500.00", "ARS"));

        pagoService.procesarWebhook("payment", "123");

        verify(pagoRepository, never()).save(any());
        verifyNoInteractions(reservaService);
    }

    @Test
    void procesarWebhook_TipoDistintoDePayment_SeIgnora() {
        pagoService.procesarWebhook("merchant_order", "123");

        verifyNoInteractions(pasarelaPagos, pagoRepository);
    }

    @Test
    void procesarWebhook_MismoPagoMp_ReutilizaSuRegistro() {
        Pago enProceso = Pago.builder().id(5L).reserva(reserva).usuario(jugador).monto(new BigDecimal("1500.00"))
                .metodo(MetodoPago.MERCADOPAGO).estado(EstadoPago.PENDIENTE).mpPaymentId("123").build();
        when(pagoRepository.findByMpPaymentId("123")).thenReturn(Optional.of(enProceso));
        when(pasarelaPagos.consultarPago("123")).thenReturn(pagoMp("approved", "1", "1500.00", "ARS"));
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        pagoService.procesarWebhook("payment", "123");

        verify(pagoRepository, never()).findByReservaId(any());
        assertEquals(EstadoPago.APROBADO, enProceso.getEstado());
    }

    @Test
    void procesarWebhook_RejectedPayment() {
        Pago pendiente = Pago.builder().id(5L).reserva(reserva).usuario(jugador).monto(new BigDecimal("1500.00"))
                .metodo(MetodoPago.MERCADOPAGO).estado(EstadoPago.PENDIENTE).build();
        when(pagoRepository.findByMpPaymentId("123")).thenReturn(Optional.empty());
        when(pasarelaPagos.consultarPago("123")).thenReturn(pagoMp("rejected", "1", "1500.00", "ARS"));
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReservaId(1L)).thenReturn(List.of(pendiente));

        pagoService.procesarWebhook("payment", "123");

        assertEquals(EstadoPago.RECHAZADO, pendiente.getEstado());
        verify(reservaService, never()).confirmarReserva(any());
    }

    @Test
    void procesarWebhook_CobroDeConsumosConPoint_MarcaLosConsumosComoPagados() {
        Pago consumos = Pago.builder().id(77L).reserva(reserva).usuario(jugador).monto(new BigDecimal("3000.00"))
                .metodo(MetodoPago.MERCADOPAGO_POINT).estado(EstadoPago.PENDIENTE).concepto(ConceptoPago.CONSUMOS).build();
        when(pagoRepository.findByMpPaymentId("123")).thenReturn(Optional.empty());
        when(pasarelaPagos.consultarPago("123")).thenReturn(pagoMp("approved", "CONSUMOS-77", "3000.00", "ARS"));
        when(pagoRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(consumos));

        pagoService.procesarWebhook("payment", "123");

        assertEquals(EstadoPago.APROBADO, consumos.getEstado());
        verify(consumoService).marcarConsumosComoPagados(77L);
        verifyNoInteractions(reservaService);
    }

    @Test
    void procesarWebhook_CobroDeConsumosRechazado_LiberaLosConsumos() {
        Pago consumos = Pago.builder().id(77L).reserva(reserva).usuario(jugador).monto(new BigDecimal("3000.00"))
                .metodo(MetodoPago.MERCADOPAGO_POINT).estado(EstadoPago.PENDIENTE).concepto(ConceptoPago.CONSUMOS).build();
        when(pagoRepository.findByMpPaymentId("123")).thenReturn(Optional.empty());
        when(pasarelaPagos.consultarPago("123")).thenReturn(pagoMp("rejected", "CONSUMOS-77", "3000.00", "ARS"));
        when(pagoRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(consumos));

        pagoService.procesarWebhook("payment", "123");

        assertEquals(EstadoPago.RECHAZADO, consumos.getEstado());
        verify(consumoService).liberarConsumosDelPago(77L);
    }

    // ---------- consulta y cobro en mostrador ----------

    @Test
    void obtenerPorId_HappyPath() {
        Pago pago = Pago.builder().id(5L).usuario(jugador).build();
        PagoResponse response = new PagoResponse(5L, 1L, null, ConceptoPago.TURNO, 1L, "juan@example.com", new BigDecimal("1500.00"),
                MetodoPago.MERCADOPAGO, EstadoPago.APROBADO, null, null, null, null);
        when(pagoRepository.findById(5L)).thenReturn(Optional.of(pago));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));
        when(pagoMapper.toResponse(pago)).thenReturn(response);

        assertEquals(response, pagoService.obtenerPorId(5L, "juan@example.com"));
    }

    @Test
    void obtenerPorId_DeOtroJugador_RespondeNoEncontrado() {
        Usuario otro = Usuario.builder().id(2L).rol(RolUsuario.JUGADOR).build();
        Pago pago = Pago.builder().id(5L).usuario(jugador).build();
        when(pagoRepository.findById(5L)).thenReturn(Optional.of(pago));
        when(usuarioRepository.findByEmail("otro@example.com")).thenReturn(Optional.of(otro));

        assertThrows(ResourceNotFoundException.class, () -> pagoService.obtenerPorId(5L, "otro@example.com"));
    }

    @Test
    void registrarCobroMostrador_TurnoPendiente_RegistraPagoYConfirma() {
        Usuario recepcion = Usuario.builder().id(4L).email("recepcion@example.com").rol(RolUsuario.RECEPCIONISTA).build();
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("recepcion@example.com")).thenReturn(Optional.of(recepcion));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        pagoService.registrarCobroMostrador(1L, MetodoPago.EFECTIVO, "recepcion@example.com");

        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(captor.capture());
        assertEquals(EstadoPago.APROBADO, captor.getValue().getEstado());
        assertEquals(recepcion, captor.getValue().getAtendidoPor());
        verify(reservaService).confirmarReserva(1L);
    }

    @Test
    void registrarCobroMostrador_TurnoConfirmadoParaCobrarAlLlegar_SoloRegistraElPago() {
        Usuario recepcion = Usuario.builder().id(4L).email("recepcion@example.com").rol(RolUsuario.RECEPCIONISTA).build();
        ReflectionTestUtils.setField(reserva, "estadoReserva", EstadoReserva.CONFIRMADA);
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("recepcion@example.com")).thenReturn(Optional.of(recepcion));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        pagoService.registrarCobroMostrador(1L, MetodoPago.TARJETA, "recepcion@example.com");

        verify(reservaService, never()).confirmarReserva(any());
    }

    @Test
    void registrarCobroMostrador_MetodoQueNoEsDeMostrador_SeRechaza() {
        assertThrows(DatosInvalidosException.class,
                () -> pagoService.registrarCobroMostrador(1L, MetodoPago.MERCADOPAGO, "recepcion@example.com"));
        verifyNoInteractions(reservaRepository);
    }

    @Test
    void registrarCobroMostrador_TurnoYaCobrado_NoCobraDosVeces() {
        ReflectionTestUtils.setField(reserva, "estadoReserva", EstadoReserva.CONFIRMADA);
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.existsByReservaIdAndEstadoAndConcepto(1L, EstadoPago.APROBADO, ConceptoPago.TURNO)).thenReturn(true);

        assertThrows(EstadoInvalidoException.class,
                () -> pagoService.registrarCobroMostrador(1L, MetodoPago.EFECTIVO, "recepcion@example.com"));
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void registrarCobroMostrador_ReservaCancelada_SeRechaza() {
        ReflectionTestUtils.setField(reserva, "estadoReserva", EstadoReserva.CANCELADA);
        when(reservaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reserva));

        assertThrows(ReservaNoModificableException.class,
                () -> pagoService.registrarCobroMostrador(1L, MetodoPago.EFECTIVO, "recepcion@example.com"));
    }
}
