package com.padel.dto.response;

import com.padel.domain.enums.RolUsuario;

public record UsuarioResponse(
    Long id,
    String nombre,
    String apellido,
    String email,
    String telefono,
    RolUsuario rol,
    boolean activo
) {}
