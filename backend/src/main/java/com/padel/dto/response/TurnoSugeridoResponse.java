package com.padel.dto.response;

import com.padel.domain.enums.BloqueHorario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** Turno libre en un horario que ambos jugadores del match tienen disponible. */
public record TurnoSugeridoResponse(
    LocalDate fecha,
    Long canchaId,
    String canchaNombre,
    Long franjaId,
    LocalTime horaInicio,
    LocalTime horaFin,
    Integer duracionMin,
    BigDecimal precio,
    BloqueHorario bloque
) {}
