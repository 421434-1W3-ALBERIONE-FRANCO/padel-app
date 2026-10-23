package com.padel.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TipoBonoRequest(
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 50, message = "El nombre no puede superar los 50 caracteres")
    String nombre,

    @Size(max = 255, message = "La descripción no puede superar los 255 caracteres")
    String descripcion,

    @NotNull(message = "Las horas son obligatorias")
    @Min(value = 1, message = "El bono debe tener al menos 1 hora")
    @Max(value = 500, message = "El bono no puede tener más de 500 horas")
    Integer horas,

    @NotNull(message = "El precio es obligatorio")
    @PositiveOrZero(message = "El precio no puede ser negativo")
    @DecimalMax(value = "99999999.99", message = "El precio es demasiado alto")
    BigDecimal precio,

    @NotNull(message = "La vigencia es obligatoria")
    @Min(value = 1, message = "La vigencia mínima es de 1 día")
    @Max(value = 730, message = "La vigencia máxima es de 2 años")
    Integer diasVigencia,

    // Opcional al crear (queda activo); permite reactivar uno dado de baja al actualizar.
    Boolean activo
) {}
