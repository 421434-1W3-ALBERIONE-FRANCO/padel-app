package com.padel.dto.response;

import com.padel.domain.enums.EstadoPartido;

import java.time.LocalDateTime;

public record PartidoTorneoResponse(
    Long id,
    Long torneoId,
    String ronda,
    Integer numeroRonda,
    Long inscripcion1Id,
    String inscripcion1Nombres,
    Long inscripcion2Id,
    String inscripcion2Nombres,
    LocalDateTime fechaHora,
    Integer setsPareja1,
    Integer setsPareja2,
    Long ganadorInscripcionId,
    EstadoPartido estado
) {}
