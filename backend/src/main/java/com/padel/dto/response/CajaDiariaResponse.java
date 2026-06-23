package com.padel.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CajaDiariaResponse(
    List<MetodoTotal> detalles
) {
    public record MetodoTotal(
        String metodo,
        BigDecimal total
    ) {}
}
