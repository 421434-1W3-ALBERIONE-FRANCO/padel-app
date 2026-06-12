package com.padel.dto.response;

public record LoginResponse(
    String token,
    UsuarioResponse usuario
) {}
