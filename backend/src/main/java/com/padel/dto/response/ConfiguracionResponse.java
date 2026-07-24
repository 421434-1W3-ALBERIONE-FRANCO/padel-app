package com.padel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ConfiguracionResponse(
    boolean comisionesHabilitadas,
    BigDecimal porcentajeComisionRecepcion,
    Integer diasAvisoVencimientoBono,
    LocalTime horaTopeCierreSesion,
    LocalDateTime updatedAt
) {}
