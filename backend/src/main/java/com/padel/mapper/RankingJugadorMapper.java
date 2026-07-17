package com.padel.mapper;

import com.padel.domain.entity.RankingJugador;
import com.padel.dto.response.RankingJugadorResponse;
import org.springframework.stereotype.Component;

@Component
public class RankingJugadorMapper {

    public RankingJugadorResponse toResponse(RankingJugador entity) {
        return new RankingJugadorResponse(
                entity.getId(),
                entity.getJugador().getId(),
                entity.getJugador().getNombre() + " " + entity.getJugador().getApellido(),
                entity.getJugador().getEmail(),
                entity.getCategoria(),
                entity.getPuntos(),
                entity.getPartidosJugados(),
                entity.getPartidosGanados()
        );
    }
}
