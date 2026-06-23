package com.padel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record DisponibilidadResponse(
    Long canchaId,
    String nombreCancha,
    LocalDate fecha,
    List<SlotDisponibilidad> slots
) {
    public record SlotDisponibilidad(
        Long franjaId,
        LocalTime horaInicio,
        LocalTime horaFin,
        Integer duracionMin,
        BigDecimal precio,
        boolean disponible,
        String motivoBloqueo
    ) {}
}
