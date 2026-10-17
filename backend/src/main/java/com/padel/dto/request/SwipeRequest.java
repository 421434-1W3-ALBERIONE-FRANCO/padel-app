package com.padel.dto.request;

import com.padel.domain.enums.TipoSwipe;
import jakarta.validation.constraints.NotNull;

public record SwipeRequest(
    @NotNull(message = "El jugador es obligatorio")
    Long usuarioId,

    @NotNull(message = "La respuesta (LIKE o PASS) es obligatoria")
    TipoSwipe tipo
) {}
