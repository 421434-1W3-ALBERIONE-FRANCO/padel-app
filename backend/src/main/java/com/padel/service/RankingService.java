package com.padel.service;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.dto.response.RankingJugadorResponse;

import java.util.List;

public interface RankingService {
    List<RankingJugadorResponse> obtenerRankingPorCategoria(CategoriaJugador categoria);
    RankingJugadorResponse obtenerMiPosicion(CategoriaJugador categoria, String usuarioEmail);
}
