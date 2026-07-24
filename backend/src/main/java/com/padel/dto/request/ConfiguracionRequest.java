package com.padel.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalTime;

public record ConfiguracionRequest(
    @NotNull(message = "Debe indicar si las comisiones están habilitadas")
    Boolean comisionesHabilitadas,

    @NotNull(message = "El porcentaje de comisión es obligatorio")
    @DecimalMin(value = "0.0", message = "El porcentaje no puede ser negativo")
    @DecimalMax(value = "100.0", message = "El porcentaje no puede superar 100")
    BigDecimal porcentajeComisionRecepcion,

    @NotNull(message = "Los días de aviso de vencimiento de bono son obligatorios")
    @Min(value = 0, message = "Los días de aviso no pueden ser negativos")
    Integer diasAvisoVencimientoBono,

    @NotNull(message = "La hora tope de cierre de sesión es obligatoria")
    LocalTime horaTopeCierreSesion
) {}
