package com.padel.service.impl;

import com.padel.domain.entity.BloqueoCancha;
import com.padel.domain.entity.Cancha;
import com.padel.domain.enums.TipoSuelo;
import com.padel.dto.request.BloqueoCanchaRequest;
import com.padel.dto.response.BloqueoCanchaResponse;
import com.padel.exception.RangoHorarioInvalidoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.exception.SolapamientoBloqueoException;
import com.padel.mapper.BloqueoCanchaMapper;
import com.padel.repository.BloqueoCanchaRepository;
import com.padel.repository.CanchaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

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
class BloqueoCanchaServiceImplTest {

    @Mock
    private BloqueoCanchaRepository bloqueoCanchaRepository;

    @Mock
    private CanchaRepository canchaRepository;

    @Mock
    private BloqueoCanchaMapper bloqueoCanchaMapper;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private BloqueoCanchaServiceImpl bloqueoCanchaService;

    private Cancha canchaMock;
    private BloqueoCancha bloqueoMock;
    private BloqueoCanchaRequest bloqueoRequest;
    private BloqueoCanchaResponse bloqueoResponse;

    @BeforeEach
    void setUp() {
        canchaMock = Cancha.builder()
                .id(1L)
                .nombre("Cancha 1")
                .tipoSuelo(TipoSuelo.BLINDEX)
                .techada(true)
                .tieneLuz(true)
                .activa(true)
                .build();

        bloqueoMock = BloqueoCancha.builder()
                .id(1L)
                .cancha(canchaMock)
                .fechaDesde(LocalDate.now().plusDays(1))
                .fechaHasta(LocalDate.now().plusDays(1))
                .horaDesde(LocalTime.of(9, 0))
                .horaHasta(LocalTime.of(12, 0))
                .motivo("Mantenimiento")
                .build();

        bloqueoRequest = new BloqueoCanchaRequest(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(1),
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                "Mantenimiento"
        );

        bloqueoResponse = new BloqueoCanchaResponse(
                1L,
                1L,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(1),
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                "Mantenimiento",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    void crear_ShouldSaveAndReturnResponse_WhenValid() {
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(canchaMock));
        when(bloqueoCanchaRepository.findOverlappingBlocks(any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(bloqueoCanchaMapper.toEntity(bloqueoRequest)).thenReturn(bloqueoMock);
        when(bloqueoCanchaRepository.save(any(BloqueoCancha.class))).thenReturn(bloqueoMock);
        when(bloqueoCanchaMapper.toResponse(bloqueoMock)).thenReturn(bloqueoResponse);

        BloqueoCanchaResponse result = bloqueoCanchaService.crear(1L, bloqueoRequest);

        assertNotNull(result);
        assertEquals(1L, result.id());
        verify(bloqueoCanchaRepository, times(1)).save(any(BloqueoCancha.class));
    }

    @Test
    void crear_ShouldThrowResourceNotFoundException_WhenCanchaDoesNotExistOrInactive() {
        when(canchaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bloqueoCanchaService.crear(1L, bloqueoRequest));
        verify(bloqueoCanchaRepository, never()).save(any(BloqueoCancha.class));
    }

    @Test
    void crear_ShouldThrowRangoHorarioInvalidoException_WhenFechaDesdeAfterFechaHasta() {
        BloqueoCanchaRequest invalidRequest = new BloqueoCanchaRequest(
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(1),
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                "Mantenimiento"
        );

        when(canchaRepository.findById(1L)).thenReturn(Optional.of(canchaMock));

        assertThrows(RangoHorarioInvalidoException.class, () -> bloqueoCanchaService.crear(1L, invalidRequest));
        verify(bloqueoCanchaRepository, never()).save(any(BloqueoCancha.class));
    }

    @Test
    void crear_ShouldThrowRangoHorarioInvalidoException_WhenHoraDesdeNotBeforeHoraHasta() {
        BloqueoCanchaRequest invalidRequest = new BloqueoCanchaRequest(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(1),
                LocalTime.of(12, 0),
                LocalTime.of(9, 0),
                "Mantenimiento"
        );

        when(canchaRepository.findById(1L)).thenReturn(Optional.of(canchaMock));

        assertThrows(RangoHorarioInvalidoException.class, () -> bloqueoCanchaService.crear(1L, invalidRequest));
        verify(bloqueoCanchaRepository, never()).save(any(BloqueoCancha.class));
    }

    @Test
    void crear_ShouldThrowSolapamientoBloqueoException_WhenOverlappingBlockExists() {
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(canchaMock));
        when(bloqueoCanchaRepository.findOverlappingBlocks(any(), any(), any(), any(), any(), any())).thenReturn(List.of(bloqueoMock));

        assertThrows(SolapamientoBloqueoException.class, () -> bloqueoCanchaService.crear(1L, bloqueoRequest));
        verify(bloqueoCanchaRepository, never()).save(any(BloqueoCancha.class));
    }

    @Test
    void obtenerPorCanchaId_ShouldReturnList_WhenCanchaExists() {
        when(canchaRepository.existsById(1L)).thenReturn(true);
        when(bloqueoCanchaRepository.findByCanchaId(1L)).thenReturn(List.of(bloqueoMock));
        when(bloqueoCanchaMapper.toResponse(bloqueoMock)).thenReturn(bloqueoResponse);

        List<BloqueoCanchaResponse> result = bloqueoCanchaService.obtenerPorCanchaId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
    }

    @Test
    void obtenerPorId_ShouldReturnResponse_WhenExists() {
        when(bloqueoCanchaRepository.findById(1L)).thenReturn(Optional.of(bloqueoMock));
        when(bloqueoCanchaMapper.toResponse(bloqueoMock)).thenReturn(bloqueoResponse);

        BloqueoCanchaResponse result = bloqueoCanchaService.obtenerPorId(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
    }

    @Test
    void actualizar_ShouldSaveAndReturnResponse_WhenValid() {
        BloqueoCanchaRequest updateRequest = new BloqueoCanchaRequest(
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(2),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                "Torneo"
        );
        BloqueoCanchaResponse updateResponse = new BloqueoCanchaResponse(
                1L,
                1L,
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(2),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                "Torneo",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(bloqueoCanchaRepository.findById(1L)).thenReturn(Optional.of(bloqueoMock));
        when(bloqueoCanchaRepository.findOverlappingBlocks(any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(bloqueoCanchaRepository.save(bloqueoMock)).thenReturn(bloqueoMock);
        when(bloqueoCanchaMapper.toResponse(bloqueoMock)).thenReturn(updateResponse);

        BloqueoCanchaResponse result = bloqueoCanchaService.actualizar(1L, updateRequest);

        assertNotNull(result);
        assertEquals(updateRequest.motivo(), result.motivo());
        verify(bloqueoCanchaRepository, times(1)).save(bloqueoMock);
    }

    @Test
    void eliminar_ShouldCallDelete_WhenExists() {
        when(bloqueoCanchaRepository.findById(1L)).thenReturn(Optional.of(bloqueoMock));

        bloqueoCanchaService.eliminar(1L);

        verify(bloqueoCanchaRepository, times(1)).delete(bloqueoMock);
    }
}
