package com.padel.dto.response;

import com.padel.domain.enums.EstadoBono;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BonoResponse(
    Long id,
    Long usuarioId,
    String usuarioEmail,
    String tipo,
    Integer horasTotales,
    Integer horasUsadas,
    BigDecimal precioPagado,
    LocalDate fechaVencimiento,
    EstadoBono estado,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
