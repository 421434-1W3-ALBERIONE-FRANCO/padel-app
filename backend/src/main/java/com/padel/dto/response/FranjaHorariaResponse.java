package com.padel.dto.response;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

public record FranjaHorariaResponse(
    Long id,
    Long canchaId,
    LocalTime horaInicio,
    LocalTime horaFin,
    Integer duracionMin,
    BigDecimal precioBase,
    BigDecimal precioNocturno,
    Set<DayOfWeek> diasAplicables,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
