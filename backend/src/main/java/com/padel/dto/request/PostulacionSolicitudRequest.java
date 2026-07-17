package com.padel.dto.request;

import jakarta.validation.constraints.Size;

public record PostulacionSolicitudRequest(
    @Size(max = 300, message = "El mensaje no puede superar los 300 caracteres")
    String mensaje
) {}
