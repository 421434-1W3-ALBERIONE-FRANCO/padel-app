package com.padel.dto.response;

import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.MetodoPago;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagoResponse(
    Long id,
    Long reservaId,
    Long usuarioId,
    String usuarioEmail,
    BigDecimal monto,
    MetodoPago metodo,
    EstadoPago estado,
    String mpPreferenceId,
    String mpPaymentId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
