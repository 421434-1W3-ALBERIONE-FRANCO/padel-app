package com.padel.dto.response;

import com.padel.domain.enums.BloqueHorario;

import java.time.DayOfWeek;

public record BloqueDisponibleResponse(
    DayOfWeek diaSemana,
    BloqueHorario bloque
) {}
