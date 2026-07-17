package com.padel.dto.response;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoTorneo;
import com.padel.domain.enums.FormatoTorneo;
import com.padel.domain.enums.TipoTorneo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TorneoResponse(
    Long id,
    String nombre,
    TipoTorneo tipo,
    FormatoTorneo formato,
    CategoriaJugador categoria,
    LocalDate fechaInicio,
    LocalDate fechaFin,
    Integer maxParejas,
    long parejasInscriptas,
    BigDecimal precioInscripcion,
    String descripcion,
    EstadoTorneo estado
) {}
