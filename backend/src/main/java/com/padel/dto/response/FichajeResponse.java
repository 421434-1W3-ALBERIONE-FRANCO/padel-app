package com.padel.dto.response;

import java.time.LocalDateTime;

/** Estado del fichaje del staff: si tiene la jornada abierta y desde cuándo (o el resumen de la que cerró). */
public record FichajeResponse(
    Long sesionId,
    boolean abierto,
    LocalDateTime inicio,
    LocalDateTime fin,
    Integer minutosTrabajados
) {
    public static FichajeResponse sinJornadaAbierta() {
        return new FichajeResponse(null, false, null, null, null);
    }
}
