package com.padel.dto.request;

import com.padel.dto.validation.PasswordSegura;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarPasswordRequest(
    @NotBlank(message = "La contraseña actual es obligatoria")
    @Size(max = 128, message = "La contraseña actual no es válida")
    String passwordActual,

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @PasswordSegura
    String passwordNueva
) {}
