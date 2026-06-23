package com.padel.service.impl;

import com.padel.domain.entity.Pago;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.MetodoPago;
import com.padel.dto.response.CajaDiariaResponse;
import com.padel.repository.PagoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReporteServiceImplTest {

    @Mock
    private PagoRepository pagoRepository;

    @InjectMocks
    private ReporteServiceImpl reporteService;

    @Test
    void obtenerCajaDiaria_DeberiaAgruparYSumarPorMetodo() {
        LocalDate hoy = LocalDate.now();

        Pago pagoEFECTIVO1 = Pago.builder().monto(new BigDecimal("100.00")).metodo(MetodoPago.EFECTIVO).estado(EstadoPago.APROBADO).build();
        Pago pagoEFECTIVO2 = Pago.builder().monto(new BigDecimal("150.00")).metodo(MetodoPago.EFECTIVO).estado(EstadoPago.APROBADO).build();
        Pago pagoMP = Pago.builder().monto(new BigDecimal("1200.00")).metodo(MetodoPago.MERCADOPAGO).estado(EstadoPago.APROBADO).build();

        List<Pago> pagosMock = Arrays.asList(pagoEFECTIVO1, pagoEFECTIVO2, pagoMP);

        when(pagoRepository.findByEstadoAndCreatedAtBetween(eq(EstadoPago.APROBADO), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(pagosMock);

        CajaDiariaResponse response = reporteService.obtenerCajaDiaria(hoy);

        assertNotNull(response);
        assertEquals(2, response.detalles().size());

        CajaDiariaResponse.MetodoTotal detalleEfectivo = response.detalles().stream()
                .filter(d -> "EFECTIVO".equals(d.metodo()))
                .findFirst()
                .orElse(null);

        CajaDiariaResponse.MetodoTotal detalleMP = response.detalles().stream()
                .filter(d -> "MERCADOPAGO".equals(d.metodo()))
                .findFirst()
                .orElse(null);

        assertNotNull(detalleEfectivo);
        assertEquals(new BigDecimal("250.00"), detalleEfectivo.total());

        assertNotNull(detalleMP);
        assertEquals(new BigDecimal("1200.00"), detalleMP.total());

        verify(pagoRepository, times(1))
                .findByEstadoAndCreatedAtBetween(eq(EstadoPago.APROBADO), any(LocalDateTime.class), any(LocalDateTime.class));
    }
}
