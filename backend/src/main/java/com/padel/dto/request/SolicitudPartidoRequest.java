package com.padel.dto.request;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.TipoSolicitud;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record SolicitudPartidoRequest(
    @NotNull(message = "El tipo de solicitud es obligatorio")
    TipoSolicitud tipoSolicitud,

    @NotNull(message = "La categoría es obligatoria")
    CategoriaJugador categoria,

    @NotNull(message = "La cantidad de jugadores faltantes es obligatoria")
    @Min(value = 1, message = "Debe faltar al menos 1 jugador")
    @Max(value = 3, message = "No pueden faltar más de 3 jugadores")
    Integer cantidadJugadoresFaltantes,

    @NotNull(message = "La fecha y hora propuesta es obligatoria")
    @Future(message = "La fecha y hora propuesta debe ser futura")
    LocalDateTime fechaHoraPropuesta,

    Long canchaId,

    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    String descripcion
) {}
