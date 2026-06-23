package com.padel.dto.response;

import com.padel.domain.enums.TipoSuelo;
import java.time.LocalDateTime;

public record CanchaResponse(
    Long id,
    String nombre,
    TipoSuelo tipoSuelo,
    boolean techada,
    boolean tieneLuz,
    boolean activa,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
