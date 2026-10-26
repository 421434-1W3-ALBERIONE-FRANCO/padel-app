package com.padel.dto.request;

import com.padel.dto.validation.PasswordSegura;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetearPasswordRequest(
    @NotBlank(message = "El token es obligatorio")
    @Size(max = 128, message = "El token no es válido")
    String token,

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @PasswordSegura
    String passwordNueva
) {}
