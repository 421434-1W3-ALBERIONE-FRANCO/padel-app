package com.padel.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ResultadoPartidoRequest(
    @NotNull(message = "Los sets de la pareja 1 son obligatorios")
    @PositiveOrZero(message = "Los sets no pueden ser negativos")
    Integer setsPareja1,

    @NotNull(message = "Los sets de la pareja 2 son obligatorios")
    @PositiveOrZero(message = "Los sets no pueden ser negativos")
    Integer setsPareja2
) {}
