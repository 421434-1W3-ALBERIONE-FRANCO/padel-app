package com.padel.dto.request;

import jakarta.validation.constraints.*;

public record ConsumoRequest(
    @NotNull(message = "El ID del producto es obligatorio")
    Long productoId,

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad mínima debe ser 1")
    Integer cantidad
) {}
