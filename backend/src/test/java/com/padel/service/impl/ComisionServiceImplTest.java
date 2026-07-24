package com.padel.service.impl;

import com.padel.domain.entity.Bono;
import com.padel.domain.entity.Comision;
import com.padel.domain.entity.ConfiguracionSistema;
import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.OrigenComision;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.response.ComisionResponse;
import com.padel.dto.response.ReporteComisionesResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.ComisionMapper;
import com.padel.repository.ComisionRepository;
import com.padel.repository.UsuarioRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComisionServiceImplTest {

    @Mock
    private ComisionRepository comisionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ConfiguracionSistemaService configuracionSistemaService;
    @Mock
    private ComisionMapper comisionMapper;

    @InjectMocks
    private ComisionServiceImpl comisionService;

    private Usuario recepcionistaMock;
    private ConfiguracionSistema configuracionMock;

    @BeforeEach
    void setUp() {
        recepcionistaMock = Usuario.builder().id(1L).email("recepcion@example.com").rol(RolUsuario.RECEPCIONISTA).build();
        configuracionMock = ConfiguracionSistema.builder()
                .id(1L)
                .comisionesHabilitadas(true)
                .porcentajeComisionRecepcion(new BigDecimal("10.00"))
                .build();
    }

    @Test
    void registrarComisionConsumo_ShouldCalculateAndSaveCommission() {
        when(configuracionSistemaService.obtenerEntidad()).thenReturn(configuracionMock);
        Pago pagoMock = Pago.builder().id(1L).build();

        comisionService.registrarComisionConsumo(recepcionistaMock, pagoMock, new BigDecimal("200.00"));

        ArgumentCaptor<Comision> captor = ArgumentCaptor.forClass(Comision.class);
        verify(comisionRepository).save(captor.capture());
        Comision saved = captor.getValue();
        assertEquals(OrigenComision.CONSUMO, saved.getOrigen());
        assertEquals(new BigDecimal("20.00"), saved.getMontoComision());
        assertEquals(recepcionistaMock, saved.getRecepcionista());
    }

    @Test
    void registrarComisionBono_ShouldCalculateAndSaveCommission() {
        when(configuracionSistemaService.obtenerEntidad()).thenReturn(configuracionMock);
        Bono bonoMock = Bono.builder().id(1L).precioPagado(new BigDecimal("8000.00")).build();

        comisionService.registrarComisionBono(recepcionistaMock, bonoMock);

        ArgumentCaptor<Comision> captor = ArgumentCaptor.forClass(Comision.class);
        verify(comisionRepository).save(captor.capture());
        Comision saved = captor.getValue();
        assertEquals(OrigenComision.BONO, saved.getOrigen());
        assertEquals(new BigDecimal("800.00"), saved.getMontoComision());
    }

    @Test
    void reporte_ShouldReturnTotalAndDetail() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(recepcionistaMock));
        Comision c1 = Comision.builder().id(1L).recepcionista(recepcionistaMock).origen(OrigenComision.CONSUMO)
                .montoBase(new BigDecimal("200.00")).porcentajeAplicado(new BigDecimal("10.00")).montoComision(new BigDecimal("20.00")).build();
        when(comisionRepository.findByRecepcionistaIdAndCreatedAtBetween(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(c1));
        ComisionResponse responseDto = new ComisionResponse(1L, OrigenComision.CONSUMO, new BigDecimal("200.00"), new BigDecimal("10.00"), new BigDecimal("20.00"), null, LocalDateTime.now());
        when(comisionMapper.toResponse(c1)).thenReturn(responseDto);

        ReporteComisionesResponse result = comisionService.reporte(1L, LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(new BigDecimal("20.00"), result.totalComisiones());
        assertEquals(1, result.detalle().size());
    }

    @Test
    void reporte_ShouldThrowResourceNotFoundException_WhenUsuarioNotFound() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                comisionService.reporte(99L, LocalDate.now().minusDays(7), LocalDate.now()));
    }
}
