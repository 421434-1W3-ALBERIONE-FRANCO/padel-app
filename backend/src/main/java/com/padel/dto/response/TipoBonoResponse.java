package com.padel.dto.response;

import java.math.BigDecimal;

public record TipoBonoResponse(
    Long id,
    String nombre,
    String descripcion,
    Integer horas,
    BigDecimal precio,
    Integer diasVigencia,
    boolean activo
) {}
