package com.padel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LiquidacionResponse(
    Long id,
    Long empleadoId,
    String empleadoEmail,
    LocalDate periodoDesde,
    LocalDate periodoHasta,
    Integer totalMinutosTrabajados,
    Integer totalMinutosBancoHoras,
    BigDecimal totalComisiones,
    LocalDateTime generadaEn
) {}
