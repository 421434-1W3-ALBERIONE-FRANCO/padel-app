package com.padel.dto.response;

import com.padel.domain.enums.TipoNotificacion;

import java.time.LocalDateTime;

public record NotificacionResponse(
    Long id,
    TipoNotificacion tipo,
    String titulo,
    String mensaje,
    boolean leida,
    Long referenciaId,
    LocalDateTime createdAt
) {}
