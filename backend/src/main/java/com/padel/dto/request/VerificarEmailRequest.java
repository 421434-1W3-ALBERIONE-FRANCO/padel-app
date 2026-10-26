package com.padel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerificarEmailRequest(
    @NotBlank(message = "El token es obligatorio")
    @Size(max = 128, message = "El token no es válido")
    String token
) {}
