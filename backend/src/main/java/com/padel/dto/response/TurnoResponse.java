package com.padel.dto.response;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record TurnoResponse(
    Long id,
    Long empleadoId,
    String empleadoEmail,
    DayOfWeek diaSemana,
    LocalTime horaInicio,
    LocalTime horaFin
) {}
