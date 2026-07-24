package com.padel.service.impl;

import com.padel.domain.entity.ConfiguracionSistema;
import com.padel.dto.request.ConfiguracionRequest;
import com.padel.dto.response.ConfiguracionResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.ConfiguracionSistemaMapper;
import com.padel.repository.ConfiguracionSistemaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfiguracionSistemaServiceImplTest {

    @Mock
    private ConfiguracionSistemaRepository configuracionSistemaRepository;
    @Mock
    private ConfiguracionSistemaMapper configuracionSistemaMapper;

    @InjectMocks
    private ConfiguracionSistemaServiceImpl configuracionSistemaService;

    private ConfiguracionSistema configuracionMock;

    @BeforeEach
    void setUp() {
        configuracionMock = ConfiguracionSistema.builder()
                .id(1L)
                .comisionesHabilitadas(false)
                .porcentajeComisionRecepcion(new BigDecimal("10.00"))
                .diasAvisoVencimientoBono(7)
                .horaTopeCierreSesion(LocalTime.of(23, 59))
                .build();
    }

    @Test
    void obtener_ShouldReturnConfiguracion_WhenExists() {
        when(configuracionSistemaRepository.findById(1L)).thenReturn(Optional.of(configuracionMock));
        ConfiguracionResponse responseDto = new ConfiguracionResponse(false, new BigDecimal("10.00"), 7, LocalTime.of(23, 59), null);
        when(configuracionSistemaMapper.toResponse(configuracionMock)).thenReturn(responseDto);

        ConfiguracionResponse result = configuracionSistemaService.obtener();

        assertNotNull(result);
        assertFalse(result.comisionesHabilitadas());
    }

    @Test
    void obtener_ShouldThrowResourceNotFoundException_WhenNotSeeded() {
        when(configuracionSistemaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> configuracionSistemaService.obtener());
    }

    @Test
    void actualizar_ShouldUpdateFieldsAndSave() {
        ConfiguracionRequest request = new ConfiguracionRequest(true, new BigDecimal("15.00"), 10, LocalTime.of(22, 0));
        when(configuracionSistemaRepository.findById(1L)).thenReturn(Optional.of(configuracionMock));
        when(configuracionSistemaRepository.save(any(ConfiguracionSistema.class))).thenReturn(configuracionMock);
        ConfiguracionResponse responseDto = new ConfiguracionResponse(true, new BigDecimal("15.00"), 10, LocalTime.of(22, 0), null);
        when(configuracionSistemaMapper.toResponse(configuracionMock)).thenReturn(responseDto);

        ConfiguracionResponse result = configuracionSistemaService.actualizar(request);

        assertTrue(result.comisionesHabilitadas());
        assertEquals(new BigDecimal("15.00"), result.porcentajeComisionRecepcion());
        assertTrue(configuracionMock.isComisionesHabilitadas());
        verify(configuracionSistemaRepository).save(configuracionMock);
    }
}
