package com.padel.dto.response;

import com.padel.domain.enums.EstadoConsumoPago;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ConsumoResponse(
    Long id,
    Long reservaId,
    Long usuarioId,
    String usuarioEmail,
    Long productoId,
    String productoNombre,
    Long pagoId,
    Integer cantidad,
    BigDecimal precioUnitario,
    BigDecimal subtotal,
    EstadoConsumoPago estadoPago,
    LocalDateTime createdAt
) {}
