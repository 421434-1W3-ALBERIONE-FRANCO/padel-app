package com.padel.dto.response;

import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.OrigenReserva;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ReservaResponse(
    Long id,
    Long usuarioId,
    String usuarioEmail,
    Long canchaId,
    String canchaNombre,
    Long franjaId,
    LocalDate fecha,
    LocalTime horaInicio,
    LocalTime horaFin,
    BigDecimal precioTotal,
    EstadoReserva estadoReserva,
    OrigenReserva origen,
    String motivoCancelacion,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
