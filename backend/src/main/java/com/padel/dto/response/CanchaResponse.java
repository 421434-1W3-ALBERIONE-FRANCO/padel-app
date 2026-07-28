package com.padel.dto.response;

import com.padel.domain.enums.TipoSuelo;
import java.time.LocalDateTime;
import java.util.List;

public record CanchaResponse(
    Long id,
    String nombre,
    TipoSuelo tipoSuelo,
    boolean techada,
    boolean tieneLuz,
    boolean activa,
    List<Integer> duracionesPermitidas,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
