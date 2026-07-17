package com.padel.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InscripcionTorneoRequest(
    @NotBlank(message = "El email del compañero es obligatorio")
    @Email(message = "El email del compañero no es válido")
    String companeroEmail
) {}
