package com.padel.dto.request;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.FormatoTorneo;
import com.padel.domain.enums.TipoTorneo;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TorneoRequest(
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    String nombre,

    @NotNull(message = "El tipo de torneo es obligatorio")
    TipoTorneo tipo,

    @NotNull(message = "El formato es obligatorio")
    FormatoTorneo formato,

    @NotNull(message = "La categoría es obligatoria")
    CategoriaJugador categoria,

    @NotNull(message = "La fecha de inicio es obligatoria")
    LocalDate fechaInicio,

    @NotNull(message = "La fecha de fin es obligatoria")
    LocalDate fechaFin,

    @NotNull(message = "El máximo de parejas es obligatorio")
    @Min(value = 2, message = "Debe admitir al menos 2 parejas")
    Integer maxParejas,

    @NotNull(message = "El precio de inscripción es obligatorio")
    @PositiveOrZero(message = "El precio de inscripción no puede ser negativo")
    BigDecimal precioInscripcion,

    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    String descripcion
) {}
