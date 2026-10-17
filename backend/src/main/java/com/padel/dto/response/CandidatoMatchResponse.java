package com.padel.dto.response;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.PosicionJuego;

import java.util.List;

/**
 * Tarjeta del mazo. Sin email ni teléfono: el contacto se libera recién cuando hay match mutuo.
 * El nombre va abreviado (nombre + inicial del apellido) por el mismo motivo.
 */
public record CandidatoMatchResponse(
    Long usuarioId,
    String nombre,
    String iniciales,
    CategoriaJugador categoria,
    PosicionJuego posicion,
    String bio,
    int puntosRanking,
    int afinidad,
    List<BloqueDisponibleResponse> disponibilidad,
    List<BloqueDisponibleResponse> coincidencias
) {}
