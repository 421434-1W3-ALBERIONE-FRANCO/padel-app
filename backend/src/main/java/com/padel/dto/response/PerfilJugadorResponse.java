package com.padel.dto.response;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.PosicionJuego;

import java.util.List;

public record PerfilJugadorResponse(
    Long id,
    Long usuarioId,
    CategoriaJugador categoria,
    PosicionJuego posicion,
    String bio,
    boolean visibleEnMatch,
    List<BloqueDisponibleResponse> disponibilidad
) {}
