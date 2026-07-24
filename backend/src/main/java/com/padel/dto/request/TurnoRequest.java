package com.padel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record TurnoRequest(
    @NotBlank(message = "El email del empleado es obligatorio")
    String empleadoEmail,

    @NotNull(message = "El día de la semana es obligatorio")
    DayOfWeek diaSemana,

    @NotNull(message = "La hora de inicio es obligatoria")
    LocalTime horaInicio,

    @NotNull(message = "La hora de fin es obligatoria")
    LocalTime horaFin
) {}
