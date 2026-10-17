package com.padel.dto.response;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.PosicionJuego;

import java.time.LocalDateTime;
import java.util.List;

/** Un match mutuo visto por uno de los dos jugadores: incluye el contacto del compañero. */
public record MatchParejaResponse(
    Long id,
    Long companeroId,
    String nombre,
    String iniciales,
    CategoriaJugador categoria,
    PosicionJuego posicion,
    String email,
    String telefono,
    List<BloqueDisponibleResponse> coincidencias,
    LocalDateTime createdAt
) {}
