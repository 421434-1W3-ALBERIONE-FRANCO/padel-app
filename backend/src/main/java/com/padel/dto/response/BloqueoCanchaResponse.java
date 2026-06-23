package com.padel.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record BloqueoCanchaResponse(
    Long id,
    Long canchaId,
    LocalDate fechaDesde,
    LocalDate fechaHasta,
    LocalTime horaDesde,
    LocalTime horaHasta,
    String motivo,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
