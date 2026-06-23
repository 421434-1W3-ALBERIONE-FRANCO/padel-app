package com.padel.service.impl;

import com.padel.domain.entity.Cancha;
import com.padel.domain.enums.TipoSuelo;
import com.padel.dto.request.CanchaRequest;
import com.padel.dto.response.CanchaResponse;
import com.padel.exception.NombreDuplicadoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.CanchaMapper;
import com.padel.repository.CanchaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CanchaServiceImplTest {

    @Mock
    private CanchaRepository canchaRepository;

    @Mock
    private CanchaMapper canchaMapper;

    @InjectMocks
    private CanchaServiceImpl canchaService;

    private Cancha canchaMock;
    private CanchaRequest canchaRequest;
    private CanchaResponse canchaResponse;

    @BeforeEach
    void setUp() {
        canchaMock = Cancha.builder()
                .id(1L)
                .nombre("Cancha Central")
                .tipoSuelo(TipoSuelo.BLINDEX)
                .techada(true)
                .tieneLuz(true)
                .activa(true)
                .build();

        canchaRequest = new CanchaRequest(
                "Cancha Central",
                TipoSuelo.BLINDEX,
                true,
                true
        );

        canchaResponse = new CanchaResponse(
                1L,
                "Cancha Central",
                TipoSuelo.BLINDEX,
                true,
                true,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    void crear_ShouldSaveAndReturnResponse_WhenNombreDoesNotExist() {
        when(canchaRepository.existsByNombreAndActivaTrue(canchaRequest.nombre())).thenReturn(false);
        when(canchaMapper.toEntity(canchaRequest)).thenReturn(canchaMock);
        when(canchaRepository.save(any(Cancha.class))).thenReturn(canchaMock);
        when(canchaMapper.toResponse(canchaMock)).thenReturn(canchaResponse);

        CanchaResponse result = canchaService.crear(canchaRequest);

        assertNotNull(result);
        assertEquals(canchaRequest.nombre(), result.nombre());
        verify(canchaRepository, times(1)).save(any(Cancha.class));
    }

    @Test
    void crear_ShouldThrowNombreDuplicadoException_WhenNombreExists() {
        when(canchaRepository.existsByNombreAndActivaTrue(canchaRequest.nombre())).thenReturn(true);

        assertThrows(NombreDuplicadoException.class, () -> canchaService.crear(canchaRequest));
        verify(canchaRepository, never()).save(any(Cancha.class));
    }

    @Test
    void obtenerActivaPorId_ShouldReturnResponse_WhenCanchaExistsAndIsActive() {
        when(canchaRepository.findByIdAndActivaTrue(1L)).thenReturn(Optional.of(canchaMock));
        when(canchaMapper.toResponse(canchaMock)).thenReturn(canchaResponse);

        CanchaResponse result = canchaService.obtenerActivaPorId(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
    }

    @Test
    void obtenerActivaPorId_ShouldThrowResourceNotFoundException_WhenCanchaDoesNotExistOrIsInactive() {
        when(canchaRepository.findByIdAndActivaTrue(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> canchaService.obtenerActivaPorId(1L));
    }

    @Test
    void actualizar_ShouldSaveAndReturnResponse_WhenValid() {
        CanchaRequest updateRequest = new CanchaRequest("Cancha Central Modificada", TipoSuelo.SINTETICO, false, false);
        CanchaResponse updateResponse = new CanchaResponse(1L, "Cancha Central Modificada", TipoSuelo.SINTETICO, false, false, true, LocalDateTime.now(), LocalDateTime.now());

        when(canchaRepository.findByIdAndActivaTrue(1L)).thenReturn(Optional.of(canchaMock));
        when(canchaRepository.existsByNombreAndActivaTrueAndIdNot(updateRequest.nombre(), 1L)).thenReturn(false);
        when(canchaRepository.save(canchaMock)).thenReturn(canchaMock);
        when(canchaMapper.toResponse(canchaMock)).thenReturn(updateResponse);

        CanchaResponse result = canchaService.actualizar(1L, updateRequest);

        assertNotNull(result);
        assertEquals(updateRequest.nombre(), result.nombre());
        assertFalse(result.techada());
        assertFalse(result.tieneLuz());
        verify(canchaRepository, times(1)).save(canchaMock);
    }

    @Test
    void desactivar_ShouldSetActivaToFalseAndSave_WhenCanchaExists() {
        when(canchaRepository.findByIdAndActivaTrue(1L)).thenReturn(Optional.of(canchaMock));
        when(canchaRepository.save(canchaMock)).thenReturn(canchaMock);

        canchaService.desactivar(1L);

        assertFalse(canchaMock.isActiva());
        verify(canchaRepository, times(1)).save(canchaMock);
    }
}
