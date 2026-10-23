package com.padel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Métricas de reservas de un período para el panel de administración. Se calculan en la base: antes el
 * panel descargaba el historial completo de reservas para contar en el navegador.
 */
public record MetricasReservasResponse(
        LocalDate desde,
        LocalDate hasta,
        long totalReservas,
        long confirmadas,
        long canceladas,
        BigDecimal ingresos,
        int tasaConfirmacion,
        List<ReservasPorDia> porDia
) {
    public record ReservasPorDia(LocalDate fecha, long cantidad) {
    }
}
