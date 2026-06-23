package com.padel.service.impl;

import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.domain.enums.TipoSuelo;
import com.padel.dto.request.FranjaHorariaRequest;
import com.padel.dto.response.FranjaHorariaResponse;
import com.padel.exception.RangoHorarioInvalidoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.exception.SolapamientoHorarioException;
import com.padel.mapper.FranjaHorariaMapper;
import com.padel.repository.CanchaRepository;
import com.padel.repository.FranjaHorariaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FranjaHorariaServiceImplTest {

    @Mock
    private FranjaHorariaRepository franjaHorariaRepository;

    @Mock
    private CanchaRepository canchaRepository;

    @Mock
    private FranjaHorariaMapper franjaHorariaMapper;

    @InjectMocks
    private FranjaHorariaServiceImpl franjaHorariaService;

    private Cancha canchaMock;
    private FranjaHoraria franjaMock;
    private FranjaHorariaRequest franjaRequest;
    private FranjaHorariaResponse franjaResponse;

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

        franjaMock = FranjaHoraria.builder()
                .id(1L)
                .cancha(canchaMock)
                .horaInicio(LocalTime.of(8, 0))
                .horaFin(LocalTime.of(9, 30))
                .duracionMin(90)
                .precioBase(BigDecimal.valueOf(1500))
                .precioNocturno(BigDecimal.valueOf(1800))
                .diasAplicables("MONDAY,TUESDAY")
                .build();

        franjaRequest = new FranjaHorariaRequest(
                LocalTime.of(8, 0),
                LocalTime.of(9, 30),
                BigDecimal.valueOf(1500),
                BigDecimal.valueOf(1800),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        );

        franjaResponse = new FranjaHorariaResponse(
                1L,
                1L,
                LocalTime.of(8, 0),
                LocalTime.of(9, 30),
                90,
                BigDecimal.valueOf(1500),
                BigDecimal.valueOf(1800),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    void crear_ShouldSaveAndReturnResponse_WhenValid() {
        when(canchaRepository.findByIdAndActivaTrue(1L)).thenReturn(Optional.of(canchaMock));
        when(franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(1L)).thenReturn(Collections.emptyList());
        when(franjaHorariaMapper.toEntity(franjaRequest)).thenReturn(franjaMock);
        when(franjaHorariaRepository.save(any(FranjaHoraria.class))).thenReturn(franjaMock);
        when(franjaHorariaMapper.toResponse(franjaMock)).thenReturn(franjaResponse);

        FranjaHorariaResponse result = franjaHorariaService.crear(1L, franjaRequest);

        assertNotNull(result);
        assertEquals(90, result.duracionMin());
        verify(franjaHorariaRepository, times(1)).save(any(FranjaHoraria.class));
    }

    @Test
    void crear_ShouldThrowRangoHorarioInvalidoException_WhenHoraInicioIsAfterHoraFin() {
        FranjaHorariaRequest invalidRequest = new FranjaHorariaRequest(
                LocalTime.of(10, 0),
                LocalTime.of(9, 0),
                BigDecimal.valueOf(1500),
                BigDecimal.valueOf(1800),
                Set.of(DayOfWeek.MONDAY)
        );

        when(canchaRepository.findByIdAndActivaTrue(1L)).thenReturn(Optional.of(canchaMock));

        assertThrows(RangoHorarioInvalidoException.class, () -> franjaHorariaService.crear(1L, invalidRequest));
        verify(franjaHorariaRepository, never()).save(any(FranjaHoraria.class));
    }

    @Test
    void crear_ShouldThrowSolapamientoHorarioException_WhenOverlapExists() {
        // Franja existente en base de datos: 8:00 - 9:30 Lunes/Martes
        when(canchaRepository.findByIdAndActivaTrue(1L)).thenReturn(Optional.of(canchaMock));
        when(franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(1L)).thenReturn(List.of(franjaMock));

        // Petición superpuesta: 9:00 - 10:30 Lunes/Miércoles
        FranjaHorariaRequest overlapRequest = new FranjaHorariaRequest(
                LocalTime.of(9, 0),
                LocalTime.of(10, 30),
                BigDecimal.valueOf(1500),
                BigDecimal.valueOf(1800),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        );

        assertThrows(SolapamientoHorarioException.class, () -> franjaHorariaService.crear(1L, overlapRequest));
        verify(franjaHorariaRepository, never()).save(any(FranjaHoraria.class));
    }

    @Test
    void obtenerPorId_ShouldReturnResponse_WhenValid() {
        when(franjaHorariaRepository.findById(1L)).thenReturn(Optional.of(franjaMock));
        when(franjaHorariaMapper.toResponse(franjaMock)).thenReturn(franjaResponse);

        FranjaHorariaResponse result = franjaHorariaService.obtenerPorId(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
    }

    @Test
    void actualizar_ShouldSaveAndReturnResponse_WhenValid() {
        FranjaHorariaRequest updateRequest = new FranjaHorariaRequest(
                LocalTime.of(10, 0),
                LocalTime.of(11, 30),
                BigDecimal.valueOf(1700),
                BigDecimal.valueOf(2000),
                Set.of(DayOfWeek.SATURDAY)
        );
        FranjaHorariaResponse updateResponse = new FranjaHorariaResponse(
                1L,
                1L,
                LocalTime.of(10, 0),
                LocalTime.of(11, 30),
                90,
                BigDecimal.valueOf(1700),
                BigDecimal.valueOf(2000),
                Set.of(DayOfWeek.SATURDAY),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(franjaHorariaRepository.findById(1L)).thenReturn(Optional.of(franjaMock));
        when(franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(1L)).thenReturn(List.of(franjaMock));
        when(franjaHorariaMapper.daysToString(updateRequest.diasAplicables())).thenReturn("SATURDAY");
        when(franjaHorariaRepository.save(franjaMock)).thenReturn(franjaMock);
        when(franjaHorariaMapper.toResponse(franjaMock)).thenReturn(updateResponse);

        FranjaHorariaResponse result = franjaHorariaService.actualizar(1L, updateRequest);

        assertNotNull(result);
        assertEquals(updateRequest.horaInicio(), result.horaInicio());
        verify(franjaHorariaRepository, times(1)).save(franjaMock);
    }

    @Test
    void eliminar_ShouldCallDelete_WhenValid() {
        when(franjaHorariaRepository.findById(1L)).thenReturn(Optional.of(franjaMock));

        franjaHorariaService.eliminar(1L);

        verify(franjaHorariaRepository, times(1)).delete(franjaMock);
    }
}
