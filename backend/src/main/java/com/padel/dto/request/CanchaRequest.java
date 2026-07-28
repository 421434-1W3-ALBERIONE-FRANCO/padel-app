package com.padel.dto.request;

import com.padel.domain.enums.TipoSuelo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CanchaRequest(
    @NotBlank(message = "El nombre de la cancha es obligatorio")
    String nombre,

    @NotNull(message = "El tipo de suelo es obligatorio")
    TipoSuelo tipoSuelo,

    @NotNull(message = "Debe especificar si es techada o no")
    Boolean techada,

    @NotNull(message = "Debe especificar si tiene luz o no")
    Boolean tieneLuz,

    // Duraciones de reserva permitidas en minutos (ej. [60, 90, 120, 180]). Opcional: default [90].
    List<Integer> duracionesPermitidas
) {}
