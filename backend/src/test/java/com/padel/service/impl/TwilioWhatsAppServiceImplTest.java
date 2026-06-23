package com.padel.service.impl;

import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
class TwilioWhatsAppServiceImplTest {

    @InjectMocks
    private TwilioWhatsAppServiceImpl notificacionService;

    private Reserva reservaMock;

    @BeforeEach
    void setUp() {
        Usuario usuarioMock = Usuario.builder()
                .id(1L)
                .nombre("Juan")
                .email("juan@example.com")
                .telefono("123456789")
                .build();

        Cancha canchaMock = Cancha.builder()
                .id(1L)
                .nombre("Cancha Central")
                .build();

        reservaMock = Reserva.builder()
                .id(1L)
                .usuario(usuarioMock)
                .cancha(canchaMock)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(18, 0))
                .horaFin(LocalTime.of(19, 30))
                .precioTotal(new BigDecimal("1200.00"))
                .motivoCancelacion("Clima adverso")
                .build();

        // Configurar valores por defecto en el servicio (simulación de inyección de propiedades)
        ReflectionTestUtils.setField(notificacionService, "accountSid", "");
        ReflectionTestUtils.setField(notificacionService, "authToken", "");
        ReflectionTestUtils.setField(notificacionService, "whatsappFrom", "whatsapp:+14155238886");
        ReflectionTestUtils.setField(notificacionService, "whatsappToPrefix", "whatsapp:");
    }

    @Test
    void enviarConfirmacion_ShouldNotThrowException_WhenTwilioNotInitialized() {
        assertDoesNotThrow(() -> notificacionService.enviarConfirmacion(reservaMock));
    }

    @Test
    void enviarRecordatorio_ShouldNotThrowException_WhenTwilioNotInitialized() {
        assertDoesNotThrow(() -> notificacionService.enviarRecordatorio(reservaMock));
    }

    @Test
    void enviarCancelacion_ShouldNotThrowException_WhenTwilioNotInitialized() {
        assertDoesNotThrow(() -> notificacionService.enviarCancelacion(reservaMock));
    }

    @Test
    void enviarConfirmacion_ShouldNotSend_WhenTelefonoIsEmpty() {
        reservaMock.getUsuario().setTelefono("");
        assertDoesNotThrow(() -> notificacionService.enviarConfirmacion(reservaMock));
    }
}
