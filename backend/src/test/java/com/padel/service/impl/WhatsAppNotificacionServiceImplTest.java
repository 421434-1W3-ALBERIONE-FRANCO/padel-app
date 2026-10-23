package com.padel.service.impl;

import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WhatsAppNotificacionServiceImplTest {

    @Mock
    private BandejaSalida bandejaSalida;

    @InjectMocks
    private WhatsAppNotificacionServiceImpl servicio;

    private Reserva reserva;

    @BeforeEach
    void setUp() {
        Usuario usuario = Usuario.builder().nombre("Juan").telefono(" +5491100000003 ").build();
        reserva = Reserva.builder().usuario(usuario).cancha(Cancha.builder().nombre("Cancha 1").build())
                .fecha(LocalDate.of(2031, 5, 20)).horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30))
                .precioTotal(new BigDecimal("1500.00")).build();
    }

    @Test
    void laConfirmacionSeEncolaConElTelefonoNormalizado() {
        servicio.enviarConfirmacion(reserva);

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(bandejaSalida).encolarWhatsApp(eq("+5491100000003"), texto.capture());
        assertTrue(texto.getValue().contains("CONFIRMADA"));
        assertTrue(texto.getValue().contains("18:00"));
    }

    @Test
    void elRecordatorioYLaCancelacionTambienSeEncolan() {
        reserva.cancelar("Lluvia");
        servicio.enviarRecordatorio(reserva);
        servicio.enviarCancelacion(reserva);

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(bandejaSalida, org.mockito.Mockito.times(2)).encolarWhatsApp(anyString(), texto.capture());
        assertTrue(texto.getAllValues().get(1).contains("Motivo: Lluvia"));
    }

    @Test
    void sinTelefonoNoSeEncolaNada() {
        reserva.getUsuario().setTelefono("  ");

        servicio.enviarConfirmacion(reserva);

        verify(bandejaSalida, never()).encolarWhatsApp(anyString(), anyString());
    }
}
