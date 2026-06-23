package com.padel.dto.request;

import jakarta.validation.constraints.NotNull;

public record PreferenciaRequest(
    @NotNull(message = "El ID de reserva es obligatorio") Long reservaId
) {}
