package com.padel.dto.response;

import com.padel.domain.enums.EstadoComision;
import com.padel.domain.enums.OrigenComision;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ComisionResponse(
    Long id,
    OrigenComision origen,
    BigDecimal montoBase,
    BigDecimal porcentajeAplicado,
    BigDecimal montoComision,
    EstadoComision estado,
    LocalDateTime createdAt
) {}
