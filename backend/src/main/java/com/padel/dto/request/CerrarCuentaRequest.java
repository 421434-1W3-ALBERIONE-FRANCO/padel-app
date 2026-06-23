package com.padel.dto.request;

import com.padel.domain.enums.MetodoPago;
import jakarta.validation.constraints.NotNull;

public record CerrarCuentaRequest(
    @NotNull(message = "El método de pago es obligatorio")
    MetodoPago metodo
) {}
