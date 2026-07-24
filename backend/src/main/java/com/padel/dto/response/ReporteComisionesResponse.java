package com.padel.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ReporteComisionesResponse(
    Long recepcionistaId,
    String recepcionistaEmail,
    BigDecimal totalComisiones,
    List<ComisionResponse> detalle
) {}
