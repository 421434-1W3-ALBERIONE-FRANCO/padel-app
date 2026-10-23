package com.padel.dto.response;

import com.padel.domain.enums.EstadoInscripcion;

public record InscripcionTorneoResponse(
    Long id,
    Long torneoId,
    Long jugador1Id,
    String jugador1Nombre,
    Long jugador2Id,
    String jugador2Nombre,
    EstadoInscripcion estado
) {}
