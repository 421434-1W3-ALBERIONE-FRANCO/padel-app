package com.padel.service.impl;

import com.padel.domain.entity.BloqueoCancha;
import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.domain.enums.TipoSuelo;
import com.padel.dto.response.DisponibilidadResponse;
import com.padel.dto.response.DisponibilidadResponse.SlotDisponibilidad;
import com.padel.exception.ResourceNotFoundException;
import com.padel.repository.BloqueoCanchaRepository;
import com.padel.repository.CanchaRepository;
import com.padel.repository.FranjaHorariaRepository;
import com.padel.repository.ReservaRepository;
import com.padel.service.RedisLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisponibilidadServiceImplTest {

    @Mock
    private CanchaRepository canchaRepository;

    @Mock
    private FranjaHorariaRepository franjaHorariaRepository;

    @Mock
    private BloqueoCanchaRepository bloqueoCanchaRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private RedisLockService redisLockService;

    @InjectMocks
    private DisponibilidadServiceImpl disponibilidadService;

    private Cancha canchaMock;
    private FranjaHoraria franjaDiurna;
    private FranjaHoraria franjaNocturna;
    private BloqueoCancha bloqueoMock;
    private LocalDate fechaLunes;

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

        franjaDiurna = FranjaHoraria.builder()
                .id(1L)
                .cancha(canchaMock)
                .horaInicio(LocalTime.of(8, 0))
                .horaFin(LocalTime.of(9, 30))
                .duracionMin(90)
                .precioBase(BigDecimal.valueOf(1000))
                .precioNocturno(BigDecimal.valueOf(1200))
                .diasAplicables("MONDAY,TUESDAY")
                .build();

        franjaNocturna = FranjaHoraria.builder()
                .id(2L)
                .cancha(canchaMock)
                .horaInicio(LocalTime.of(18, 0))
                .horaFin(LocalTime.of(19, 30))
                .duracionMin(90)
                .precioBase(BigDecimal.valueOf(1500))
                .precioNocturno(BigDecimal.valueOf(2000))
                .diasAplicables("MONDAY,TUESDAY")
                .build();

        fechaLunes = LocalDate.now().plusWeeks(1);
        while (fechaLunes.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
            fechaLunes = fechaLunes.plusDays(1);
        }

        bloqueoMock = BloqueoCancha.builder()
                .id(1L)
                .cancha(canchaMock)
                .fechaDesde(fechaLunes)
                .fechaHasta(fechaLunes)
                .horaDesde(LocalTime.of(8, 30))
                .horaHasta(LocalTime.of(10, 0))
                .motivo("Mantenimiento")
                .build();
    }

    @Test
    void obtenerDisponibilidad_ShouldReturnSlots_WhenCanchaExists() {
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(canchaMock));
        when(franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(1L)).thenReturn(List.of(franjaDiurna, franjaNocturna));
        when(bloqueoCanchaRepository.findBlocksForDate(1L, fechaLunes)).thenReturn(Collections.emptyList());

        DisponibilidadResponse result = disponibilidadService.obtenerDisponibilidad(1L, fechaLunes);

        assertNotNull(result);
        assertEquals(1L, result.canchaId());
        assertEquals("Cancha 1", result.nombreCancha());
        assertEquals(2, result.slots().size());

        // Validar precios calculados
        SlotDisponibilidad slotDiurno = result.slots().stream().filter(s -> s.franjaId().equals(1L)).findFirst().orElseThrow();
        assertEquals(BigDecimal.valueOf(1000), slotDiurno.precio()); // 08:00 (Diurno) -> Precio Base
        assertTrue(slotDiurno.disponible());

        SlotDisponibilidad slotNocturno = result.slots().stream().filter(s -> s.franjaId().equals(2L)).findFirst().orElseThrow();
        assertEquals(BigDecimal.valueOf(2000), slotNocturno.precio()); // 18:00 (Nocturno) -> Precio Nocturno
        assertTrue(slotNocturno.disponible());
    }

    @Test
    void obtenerDisponibilidad_ShouldMarkSlotsAsUnavailable_WhenBloqueoOverlaps() {
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(canchaMock));
        when(franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(1L)).thenReturn(List.of(franjaDiurna, franjaNocturna));
        when(bloqueoCanchaRepository.findBlocksForDate(1L, fechaLunes)).thenReturn(List.of(bloqueoMock));

        DisponibilidadResponse result = disponibilidadService.obtenerDisponibilidad(1L, fechaLunes);

        assertNotNull(result);

        // Franja Diurna: 08:00 - 09:30. Bloqueo: 08:30 - 10:00 -> Se solapan, debe ser no disponible
        SlotDisponibilidad slotDiurno = result.slots().stream().filter(s -> s.franjaId().equals(1L)).findFirst().orElseThrow();
        assertFalse(slotDiurno.disponible());
        assertEquals("Mantenimiento", slotDiurno.motivoBloqueo());

        // Franja Nocturna: 18:00 - 19:30. No se solapa con el bloqueo -> Disponible
        SlotDisponibilidad slotNocturno = result.slots().stream().filter(s -> s.franjaId().equals(2L)).findFirst().orElseThrow();
        assertTrue(slotNocturno.disponible());
        assertNull(slotNocturno.motivoBloqueo());
    }

    @Test
    void obtenerDisponibilidad_ShouldMarkSlotsAsUnavailable_WhenFechaInPast() {
        LocalDate fechaPasada = LocalDate.of(2026, 6, 8); // Lunes en el pasado
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(canchaMock));
        when(franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(1L)).thenReturn(List.of(franjaDiurna));

        DisponibilidadResponse result = disponibilidadService.obtenerDisponibilidad(1L, fechaPasada);

        assertNotNull(result);
        assertFalse(result.slots().isEmpty());
        SlotDisponibilidad slot = result.slots().get(0);
        assertFalse(slot.disponible());
        assertEquals("Fecha pasada", slot.motivoBloqueo());
    }

    @Test
    void obtenerDisponibilidad_ShouldThrowResourceNotFoundException_WhenCanchaDoesNotExistOrInactive() {
        LocalDate fechaLunes = LocalDate.of(2026, 6, 15);
        when(canchaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> disponibilidadService.obtenerDisponibilidad(1L, fechaLunes));
    }
}
