package com.padel.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record LiquidacionRequest(
    @NotNull(message = "El id del empleado es obligatorio")
    Long empleadoId,

    @NotNull(message = "La fecha desde es obligatoria")
    LocalDate desde,

    @NotNull(message = "La fecha hasta es obligatoria")
    LocalDate hasta
) {}
