package com.padel.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductoRequest(
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    String nombre,

    @NotBlank(message = "La categoría es obligatoria")
    @Size(max = 50, message = "La categoría no puede superar los 50 caracteres")
    String categoria,

    @NotNull(message = "El precio es obligatorio")
    @Positive(message = "El precio debe ser mayor a cero")
    BigDecimal precio,

    @NotNull(message = "El stock es obligatorio")
    @PositiveOrZero(message = "El stock no puede ser negativo")
    Integer stock,

    boolean activo
) {}
