package com.padel.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

public record FranjaHorariaRequest(
    @NotNull(message = "La hora de inicio es obligatoria")
    LocalTime horaInicio,

    @NotNull(message = "La hora de fin es obligatoria")
    LocalTime horaFin,

    @NotNull(message = "El precio base es obligatorio")
    @DecimalMin(value = "0.0", message = "El precio base no puede ser negativo")
    BigDecimal precioBase,

    @NotNull(message = "El precio nocturno es obligatorio")
    @DecimalMin(value = "0.0", message = "El precio nocturno no puede ser negativo")
    BigDecimal precioNocturno,

    @NotEmpty(message = "Debe especificar al menos un día aplicable")
    Set<DayOfWeek> diasAplicables
) {}
