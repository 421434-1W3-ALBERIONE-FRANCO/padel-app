package com.padel.service.impl;

import com.padel.domain.entity.BloqueoCancha;
import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.domain.entity.Reserva;
import com.padel.domain.enums.EstadoReserva;
import com.padel.dto.response.DisponibilidadResponse;
import com.padel.dto.response.DisponibilidadResponse.OpcionDuracion;
import com.padel.dto.response.DisponibilidadResponse.SlotDisponibilidad;
import com.padel.exception.ResourceNotFoundException;
import com.padel.repository.BloqueoCanchaRepository;
import com.padel.repository.CanchaRepository;
import com.padel.repository.FranjaHorariaRepository;
import com.padel.repository.ReservaRepository;
import com.padel.service.DisponibilidadService;
import com.padel.service.RedisLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisponibilidadServiceImpl implements DisponibilidadService {

    private final CanchaRepository canchaRepository;
    private final FranjaHorariaRepository franjaHorariaRepository;
    private final BloqueoCanchaRepository bloqueoCanchaRepository;
    private final ReservaRepository reservaRepository;
    private final RedisLockService redisLockService;

    @Override
    public DisponibilidadResponse obtenerDisponibilidad(Long canchaId, LocalDate fecha) {
        Cancha cancha = canchaRepository.findById(canchaId)
                .filter(Cancha::isActiva)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha no encontrada o inactiva"));

        DayOfWeek dayOfWeek = fecha.getDayOfWeek();
        List<FranjaHoraria> allFranjas = franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(canchaId);

        List<FranjaHoraria> applicableFranjas = allFranjas.stream()
                .filter(f -> stringToDays(f.getDiasAplicables()).contains(dayOfWeek))
                .sorted(Comparator.comparing(FranjaHoraria::getHoraInicio))
                .toList();

        List<BloqueoCancha> blocks = bloqueoCanchaRepository.findBlocksForDate(canchaId, fecha);
        List<Reserva> activeReservations = reservaRepository.findActiveReservationsByCanchaAndDate(canchaId, fecha);
        Set<Integer> duraciones = parseDuraciones(cancha.getDuracionesPermitidas());

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        List<SlotDisponibilidad> slots = applicableFranjas.stream().map(f -> {
            LocalTime start = f.getHoraInicio();
            LocalTime end = f.getHoraFin();

            boolean esNocturno = !start.isBefore(LocalTime.of(18, 0));
            BigDecimal precio = esNocturno ? f.getPrecioNocturno() : f.getPrecioBase();

            boolean disponible = true;
            String motivo = null;

            if (fecha.isBefore(today)) {
                disponible = false;
                motivo = "Fecha pasada";
            } else if (fecha.equals(today) && start.isBefore(now)) {
                disponible = false;
                motivo = "Horario pasado";
            } else {
                Optional<BloqueoCancha> activeBlock = blocks.stream()
                        .filter(b -> start.isBefore(b.getHoraHasta()) && b.getHoraDesde().isBefore(end))
                        .findFirst();

                if (activeBlock.isPresent()) {
                    disponible = false;
                    motivo = activeBlock.get().getMotivo();
                } else {
                    Optional<Reserva> activeReserva = activeReservations.stream()
                            .filter(r -> start.isBefore(r.getHoraFin()) && r.getHoraInicio().isBefore(end))
                            .findFirst();

                    if (activeReserva.isPresent()) {
                        disponible = false;
                        motivo = activeReserva.get().getEstadoReserva() == EstadoReserva.PENDIENTE_PAGO
                                ? "En proceso de pago"
                                : "Reservado";
                    } else if (redisLockService.hasLock(canchaId, fecha, start)) {
                        disponible = false;
                        motivo = "En proceso de pago";
                    }
                }
            }

            List<OpcionDuracion> opciones = disponible
                    ? calcularOpciones(start, applicableFranjas, activeReservations, blocks, duraciones)
                    : List.of();

            return new SlotDisponibilidad(
                    f.getId(), start, end, f.getDuracionMin(), precio, disponible, motivo, opciones
            );
        }).toList();

        return new DisponibilidadResponse(canchaId, cancha.getNombre(), fecha, slots);
    }

    // Para un inicio dado, calcula las duraciones permitidas que entran en la ventana libre contigua
    // (limitada por el fin del horario de apertura, la próxima reserva y el próximo bloqueo).
    private List<OpcionDuracion> calcularOpciones(LocalTime start, List<FranjaHoraria> applicable,
                                                  List<Reserva> reservas, List<BloqueoCancha> blocks,
                                                  Set<Integer> duraciones) {
        // 1. Fin del horario de apertura contiguo desde 'start'.
        LocalTime openEnd = start;
        boolean extended = true;
        while (extended) {
            extended = false;
            for (FranjaHoraria f : applicable) {
                boolean contigua = !f.getHoraInicio().isAfter(openEnd) && f.getHoraFin().isAfter(openEnd);
                if (contigua) {
                    openEnd = f.getHoraFin();
                    extended = true;
                }
            }
        }

        // 2. Limitar por la próxima reserva/bloqueo que arranca en o después de 'start'.
        LocalTime limite = openEnd;
        for (Reserva r : reservas) {
            if (!r.getHoraInicio().isBefore(start) && r.getHoraInicio().isBefore(limite)) {
                limite = r.getHoraInicio();
            }
        }
        for (BloqueoCancha b : blocks) {
            if (!b.getHoraDesde().isBefore(start) && b.getHoraDesde().isBefore(limite)) {
                limite = b.getHoraDesde();
            }
        }

        // 3. Franjas que cubren la ventana [start, limite], para prorratear precios.
        final LocalTime ventanaFin = limite;
        List<FranjaHoraria> cubiertasVentana = applicable.stream()
                .filter(f -> f.getHoraInicio().isBefore(ventanaFin) && start.isBefore(f.getHoraFin()))
                .toList();

        List<OpcionDuracion> opciones = new ArrayList<>();
        for (Integer d : duraciones.stream().sorted().toList()) {
            LocalTime horaFin = start.plusMinutes(d);
            if (!horaFin.isAfter(start)) continue;      // se pasó de medianoche
            if (horaFin.isAfter(limite)) continue;       // no entra en la ventana libre
            BigDecimal precioOpcion = prorratear(cubiertasVentana, start, horaFin);
            opciones.add(new OpcionDuracion(d, horaFin, precioOpcion));
        }
        return opciones;
    }

    private BigDecimal prorratear(List<FranjaHoraria> cubiertas, LocalTime horaInicio, LocalTime horaFin) {
        BigDecimal total = BigDecimal.ZERO;
        for (FranjaHoraria f : cubiertas) {
            LocalTime overlapStart = horaInicio.isAfter(f.getHoraInicio()) ? horaInicio : f.getHoraInicio();
            LocalTime overlapEnd = horaFin.isBefore(f.getHoraFin()) ? horaFin : f.getHoraFin();
            long overlapMin = Duration.between(overlapStart, overlapEnd).toMinutes();
            if (overlapMin <= 0) continue;
            boolean noct = !f.getHoraInicio().isBefore(LocalTime.of(18, 0));
            BigDecimal precioFranja = noct ? f.getPrecioNocturno() : f.getPrecioBase();
            BigDecimal fraccion = BigDecimal.valueOf(overlapMin)
                    .divide(BigDecimal.valueOf(f.getDuracionMin()), 6, RoundingMode.HALF_UP);
            total = total.add(precioFranja.multiply(fraccion));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private Set<Integer> parseDuraciones(String s) {
        if (s == null || s.isBlank()) {
            return Set.of(90);
        }
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .map(Integer::valueOf)
                .collect(Collectors.toSet());
    }

    private Set<DayOfWeek> stringToDays(String diasString) {
        if (diasString == null || diasString.trim().isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(diasString.split(","))
                .map(String::trim)
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
    }
}
