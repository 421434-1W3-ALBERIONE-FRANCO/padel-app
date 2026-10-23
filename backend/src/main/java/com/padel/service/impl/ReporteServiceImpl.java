package com.padel.service.impl;

import com.padel.domain.entity.Bono;
import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.MetodoPago;
import com.padel.dto.response.CajaDiariaResponse.MetodoTotal;
import com.padel.dto.response.CajaDiariaResponse.MovimientoCaja;
import com.padel.dto.response.CajaDiariaResponse.PendienteCobro;
import com.padel.dto.response.CajaDiariaResponse;
import com.padel.dto.response.MetricasReservasResponse.ReservasPorDia;
import com.padel.dto.response.MetricasReservasResponse;
import com.padel.exception.DatosInvalidosException;
import com.padel.repository.PagoRepository;
import com.padel.repository.ReservaRepository;
import com.padel.service.ReporteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReporteServiceImpl implements ReporteService {

    // Orden de los totales: primero lo que se cobra en el mostrador; al final el bono, que no es ingreso.
    private static final List<MetodoPago> ORDEN_METODOS = List.of(
            MetodoPago.EFECTIVO, MetodoPago.TARJETA, MetodoPago.MERCADOPAGO_POINT, MetodoPago.MERCADOPAGO, MetodoPago.BONO);
    private static final DateTimeFormatter DIA_Y_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final PagoRepository pagoRepository;
    private final ReservaRepository reservaRepository;

    @Override
    public CajaDiariaResponse obtenerCajaDiaria(LocalDate fecha) {
        log.info("Generando reporte de caja diaria para fecha: {}", fecha);

        List<Pago> pagos = pagoRepository.findMovimientosDeCaja(
                EstadoPago.APROBADO, fecha.atStartOfDay(), fecha.atTime(LocalTime.MAX));

        Map<MetodoPago, List<Pago>> porMetodo = pagos.stream().collect(Collectors.groupingBy(
                Pago::getMetodo, () -> new EnumMap<>(MetodoPago.class), Collectors.toList()));

        List<MetodoTotal> detalles = porMetodo.keySet().stream()
                .sorted(Comparator.comparingInt(ReporteServiceImpl::ordenMetodo))
                .map(metodo -> new MetodoTotal(metodo.name(), sumar(porMetodo.get(metodo)),
                        porMetodo.get(metodo).size(), metodo.esIngreso()))
                .toList();

        BigDecimal totalIngresos = sumar(pagos.stream().filter(p -> p.getMetodo().esIngreso()).toList());
        BigDecimal totalEfectivo = sumar(porMetodo.getOrDefault(MetodoPago.EFECTIVO, List.of()));

        List<MovimientoCaja> movimientos = pagos.stream().map(this::aMovimiento).toList();

        List<PendienteCobro> pendientes = reservaRepository.findSinCobroDelDia(fecha).stream()
                .map(r -> new PendienteCobro(r.getId(), r.getCancha().getNombre(), r.getHoraInicio(), r.getHoraFin(),
                        nombreCompleto(r.getUsuario()), r.getPrecioTotal(), r.getEstadoReserva()))
                .toList();

        log.info("Caja del {}: {} movimientos, ingresos {}, efectivo {}, {} turnos sin cobrar",
                fecha, movimientos.size(), totalIngresos, totalEfectivo, pendientes.size());
        return new CajaDiariaResponse(fecha, totalIngresos, totalEfectivo, detalles, movimientos, pendientes);
    }

    private MovimientoCaja aMovimiento(Pago pago) {
        return new MovimientoCaja(
                pago.getId(),
                pago.getCreatedAt(),
                pago.getConcepto(),
                detalle(pago),
                nombreCompleto(pago.getUsuario()),
                pago.getMetodo(),
                pago.getMonto(),
                pago.getAtendidoPor() != null ? nombreCompleto(pago.getAtendidoPor()) : null,
                pago.getMetodo().esIngreso());
    }

    /** "Turno · Cancha Central 18/10 18:00", "Consumos · …" o "Bono 10HS · 10 hs". */
    private String detalle(Pago pago) {
        return switch (pago.getConcepto()) {
            case BONO -> {
                Bono bono = pago.getBono();
                yield bono != null ? "Bono " + bono.getTipo() + " · " + bono.getHorasTotales() + " hs" : "Venta de bono";
            }
            case CONSUMOS -> "Consumos · " + turno(pago.getReserva());
            case TURNO -> (pago.getMetodo() == MetodoPago.BONO ? "Turno con horas de bono · " : "Turno · ")
                    + turno(pago.getReserva());
            case REINTEGRO -> "Reintegro · " + (pago.getReserva() != null ? turno(pago.getReserva()) : "bono");
        };
    }

    private String turno(Reserva reserva) {
        if (reserva == null) return "";
        return reserva.getCancha().getNombre() + " " + reserva.getFecha().atTime(reserva.getHoraInicio()).format(DIA_Y_HORA);
    }

    private static String nombreCompleto(Usuario usuario) {
        return usuario.getNombre() + " " + usuario.getApellido();
    }

    private static BigDecimal sumar(List<Pago> pagos) {
        return pagos.stream().map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int ordenMetodo(MetodoPago metodo) {
        int posicion = ORDEN_METODOS.indexOf(metodo);
        return posicion < 0 ? ORDEN_METODOS.size() : posicion;
    }

    /** Ventana máxima de las métricas: suficiente para el panel (hoy, semana, mes) sin barrer años de historial. */
    static final int MAX_DIAS_METRICAS = 92;

    @Override
    public MetricasReservasResponse metricasReservas(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new DatosInvalidosException("La fecha 'hasta' no puede ser anterior a 'desde'");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= MAX_DIAS_METRICAS) {
            throw new DatosInvalidosException("El período de las métricas no puede superar los " + MAX_DIAS_METRICAS + " días");
        }

        long total = 0;
        long confirmadas = 0;
        long canceladas = 0;
        BigDecimal ingresos = BigDecimal.ZERO;
        Map<LocalDate, Long> porDia = new TreeMap<>();
        for (Object[] fila : reservaRepository.resumirPorFechaYEstado(desde, hasta)) {
            LocalDate fecha = (LocalDate) fila[0];
            EstadoReserva estado = (EstadoReserva) fila[1];
            long cantidad = ((Number) fila[2]).longValue();
            BigDecimal importe = new BigDecimal(fila[3].toString());
            total += cantidad;
            porDia.merge(fecha, cantidad, Long::sum);
            if (estado == EstadoReserva.CONFIRMADA || estado == EstadoReserva.COMPLETADA) {
                confirmadas += cantidad;
                ingresos = ingresos.add(importe);
            } else if (estado == EstadoReserva.CANCELADA) {
                canceladas += cantidad;
            }
        }

        List<ReservasPorDia> serie = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            serie.add(new ReservasPorDia(d, porDia.getOrDefault(d, 0L)));
        }
        int tasa = total == 0 ? 0 : (int) Math.round(confirmadas * 100.0 / total);
        return new MetricasReservasResponse(desde, hasta, total, confirmadas, canceladas, ingresos, tasa, serie);
    }
}
