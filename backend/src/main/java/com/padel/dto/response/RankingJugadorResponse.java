package com.padel.dto.response;

import com.padel.domain.enums.CategoriaJugador;

public record RankingJugadorResponse(
    Long id,
    Long jugadorId,
    String jugadorNombre,
    String jugadorEmail,
    CategoriaJugador categoria,
    Integer puntos,
    Integer partidosJugados,
    Integer partidosGanados
) {}
