package com.padel.dto.request;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.PosicionJuego;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PerfilJugadorRequest(
    @NotNull(message = "La categoría es obligatoria")
    CategoriaJugador categoria,

    @NotNull(message = "La posición es obligatoria")
    PosicionJuego posicion,

    @Size(max = 280, message = "La presentación no puede superar los 280 caracteres")
    String bio,

    // Opcional: si no viene, el perfil queda visible en el match.
    Boolean visibleEnMatch,

    @NotEmpty(message = "Indicá al menos un horario en el que puedas jugar")
    List<@Valid BloqueDisponibleRequest> disponibilidad
) {}
