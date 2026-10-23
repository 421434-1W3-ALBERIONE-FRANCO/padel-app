package com.padel.dto.request;

import com.padel.dto.validation.PasswordSegura;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registro público. Siempre crea un JUGADOR: el campo "rol" que antes era obligatorio (y se ignoraba) ya no
 * forma parte del contrato.
 */
public record CrearUsuarioRequest(
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    String nombre,

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
    String apellido,

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato de email no es válido")
    @Size(max = 150, message = "El email no puede superar los 150 caracteres")
    String email,

    @Pattern(regexp = "^(\\+?[0-9 ()-]{6,25})?$", message = "El teléfono solo puede tener números, espacios, guiones y un + inicial")
    String telefono,

    @NotBlank(message = "La contraseña es obligatoria")
    @PasswordSegura
    String password
) {}
