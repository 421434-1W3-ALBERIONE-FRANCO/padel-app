package com.padel.dto.response;

import com.padel.domain.enums.EstadoPostulacion;

import java.time.LocalDateTime;

public record PostulacionSolicitudResponse(
    Long id,
    Long solicitudId,
    Long jugadorId,
    String jugadorNombre,
    String jugadorEmail,
    String mensaje,
    EstadoPostulacion estado,
    LocalDateTime createdAt
) {}
