package com.padel.dto.response;

import com.padel.domain.enums.EstadoInscripcion;

public record InscripcionTorneoResponse(
    Long id,
    Long torneoId,
    Long jugador1Id,
    String jugador1Nombre,
    String jugador1Email,
    Long jugador2Id,
    String jugador2Nombre,
    String jugador2Email,
    EstadoInscripcion estado
) {}
