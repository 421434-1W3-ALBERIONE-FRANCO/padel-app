package com.padel.service.impl;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.MetodoPago;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.response.PagoResponse;
import com.padel.dto.response.PreferenciaResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.PagoMapper;
import com.padel.repository.PagoRepository;
import com.padel.repository.ReservaRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    private PagoMapper pagoMapper;

    @InjectMocks
    private PagoServiceImpl pagoService;

    private Usuario usuarioMock;
    private Cancha canchaMock;
    private Reserva reservaMock;
    private Pago pagoMock;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pagoService, "accessToken", "TEST-TOKEN");

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

        pagoMock = Pago.builder()
                .id(1L)
                .reserva(reservaMock)
                .usuario(usuarioMock)
                .monto(new BigDecimal("1200.00"))
                .metodo(MetodoPago.MERCADOPAGO)
                .estado(EstadoPago.PENDIENTE)
                .mpPreferenceId("pref-123")
                .build();
    }

    @Test
    void crearPreferencia_HappyPath() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(pagoRepository.findByReservaIdAndEstado(1L, EstadoPago.PENDIENTE)).thenReturn(Optional.empty());
        when(pagoRepository.save(any(Pago.class))).thenReturn(pagoMock);

        Preference preferenceMock = mock(Preference.class);
        when(preferenceMock.getId()).thenReturn("pref-123");
        when(preferenceMock.getInitPoint()).thenReturn("http://initpoint.com");

        try (MockedConstruction<PreferenceClient> mockedClient = mockConstruction(PreferenceClient.class,
                (mock, context) -> {
                    when(mock.create(any(PreferenceRequest.class))).thenReturn(preferenceMock);
                })) {

            PreferenciaResponse response = pagoService.crearPreferencia(1L, "juan@example.com");

            assertNotNull(response);
            assertEquals("pref-123", response.preferenceId());
            assertEquals("http://initpoint.com", response.initPoint());
            verify(pagoRepository).save(any(Pago.class));
        }
    }

    @Test
    void crearPreferencia_ShouldThrowResourceNotFoundException_WhenUserNotOwner() {
        Usuario otherUser = Usuario.builder().id(2L).email("other@example.com").rol(RolUsuario.JUGADOR).build();
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherUser));

        assertThrows(ResourceNotFoundException.class, () ->
                pagoService.crearPreferencia(1L, "other@example.com"));

        verify(pagoRepository, never()).save(any());
    }

    @Test
    void procesarWebhook_ApprovedPayment_FirstTime() {
        Payment paymentMock = mock(Payment.class);
        when(paymentMock.getStatus()).thenReturn("approved");
        when(paymentMock.getExternalReference()).thenReturn("1");

        when(pagoRepository.findByMpPaymentId("999")).thenReturn(Optional.empty());
        when(pagoRepository.findByReservaId(1L)).thenReturn(List.of(pagoMock));
        when(pagoRepository.save(any(Pago.class))).thenReturn(pagoMock);

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(999L)).thenReturn(paymentMock);
                })) {

            pagoService.procesarWebhook("payment", "999");

            assertEquals(EstadoPago.APROBADO, pagoMock.getEstado());
            assertEquals("999", pagoMock.getMpPaymentId());
            verify(pagoRepository).save(pagoMock);
            verify(reservaService).confirmarReserva(1L);
        }
    }

    @Test
    void procesarWebhook_ApprovedPayment_Idempotent() {
        pagoMock.setEstado(EstadoPago.APROBADO);
        pagoMock.setMpPaymentId("pay-999");
        when(pagoRepository.findByMpPaymentId("999")).thenReturn(Optional.of(pagoMock));

        pagoService.procesarWebhook("payment", "999");

        verify(pagoRepository, never()).save(any());
        verify(reservaService, never()).confirmarReserva(anyLong());
    }

    @Test
    void procesarWebhook_RejectedPayment() {
        Payment paymentMock = mock(Payment.class);
        when(paymentMock.getStatus()).thenReturn("rejected");
        when(paymentMock.getExternalReference()).thenReturn("1");

        when(pagoRepository.findByMpPaymentId("999")).thenReturn(Optional.empty());
        when(pagoRepository.findByReservaId(1L)).thenReturn(List.of(pagoMock));
        when(pagoRepository.save(any(Pago.class))).thenReturn(pagoMock);

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(999L)).thenReturn(paymentMock);
                })) {

            pagoService.procesarWebhook("payment", "999");

            assertEquals(EstadoPago.RECHAZADO, pagoMock.getEstado());
            assertEquals("999", pagoMock.getMpPaymentId());
            verify(pagoRepository).save(pagoMock);
            verify(reservaService, never()).confirmarReserva(anyLong());
        }
    }

    @Test
    void obtenerPorId_HappyPath() {
        when(pagoRepository.findById(1L)).thenReturn(Optional.of(pagoMock));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        PagoResponse responseDto = new PagoResponse(1L, 1L, 1L, "juan@example.com", new BigDecimal("1200.00"),
                MetodoPago.MERCADOPAGO, EstadoPago.PENDIENTE, "pref-123", null, LocalDateTime.now(), LocalDateTime.now());
        when(pagoMapper.toResponse(pagoMock)).thenReturn(responseDto);

        PagoResponse result = pagoService.obtenerPorId(1L, "juan@example.com");

        assertNotNull(result);
        assertEquals(1L, result.id());
    }
}
