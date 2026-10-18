package com.padel.dto.response;

import com.padel.domain.enums.ConceptoPago;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.MetodoPago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Caja de un día: lo cobrado (pagos aprobados) por método y movimiento, y los turnos del día que todavía no
 * se cobraron. Los turnos pagados con horas de un bono se informan aparte: no son ingreso de ese día.
 */
public record CajaDiariaResponse(
    LocalDate fecha,
    /** Todo lo que entró: todos los métodos menos BONO. */
    BigDecimal totalIngresos,
    /** Lo que tiene que haber en el cajón para el arqueo. */
    BigDecimal totalEfectivo,
    List<MetodoTotal> detalles,
    List<MovimientoCaja> movimientos,
    List<PendienteCobro> pendientesDeCobro
) {
    public record MetodoTotal(
        String metodo,
        BigDecimal total,
        long cantidad,
        boolean ingreso
    ) {}

    public record MovimientoCaja(
        Long pagoId,
        LocalDateTime fechaHora,
        ConceptoPago concepto,
        String detalle,
        String cliente,
        MetodoPago metodo,
        BigDecimal monto,
        /** Staff que registró el cobro; null en los pagos online. */
        String atendidoPor,
        boolean ingreso
    ) {}

    public record PendienteCobro(
        Long reservaId,
        String canchaNombre,
        LocalTime horaInicio,
        LocalTime horaFin,
        String cliente,
        BigDecimal monto,
        EstadoReserva estadoReserva
    ) {}
}
