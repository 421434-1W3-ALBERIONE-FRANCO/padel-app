package com.padel.dto.request;

import com.padel.domain.enums.BloqueHorario;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;

public record BloqueDisponibleRequest(
    @NotNull(message = "El día de la semana es obligatorio")
    DayOfWeek diaSemana,

    @NotNull(message = "El bloque horario es obligatorio")
    BloqueHorario bloque
) {}
