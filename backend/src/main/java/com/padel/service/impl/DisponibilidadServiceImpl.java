package com.padel.service.impl;

import com.padel.domain.entity.BloqueoCancha;
import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.domain.entity.Reserva;
import com.padel.dto.response.DisponibilidadResponse;
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
import java.time.DayOfWeek;
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

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        List<SlotDisponibilidad> slots = applicableFranjas.stream().map(f -> {
            LocalTime start = f.getHoraInicio();
            LocalTime end = f.getHoraFin();

            // Determinar si es nocturno (18:00 en adelante)
            boolean esNocturno = !start.isBefore(LocalTime.of(18, 0));
            BigDecimal precio = esNocturno ? f.getPrecioNocturno() : f.getPrecioBase();

            boolean disponible = true;
            String motivo = null;

            // 0. Validar si es una fecha/hora pasada
            if (fecha.isBefore(today)) {
                disponible = false;
                motivo = "Fecha pasada";
            } else if (fecha.equals(today) && start.isBefore(now)) {
                disponible = false;
                motivo = "Horario pasado";
            } else {
                // 1. Evaluar si hay un BloqueoCancha activo cubriendo este slot
                Optional<BloqueoCancha> activeBlock = blocks.stream()
                        .filter(b -> start.isBefore(b.getHoraHasta()) && b.getHoraDesde().isBefore(end))
                        .findFirst();

                if (activeBlock.isPresent()) {
                    disponible = false;
                    motivo = activeBlock.get().getMotivo();
                } else {
                    // 2. Evaluar si existe una Reserva CONFIRMADA o COMPLETADA
                    Optional<Reserva> activeReserva = activeReservations.stream()
                            .filter(r -> start.isBefore(r.getHoraFin()) && r.getHoraInicio().isBefore(end))
                            .findFirst();

                    if (activeReserva.isPresent()) {
                        disponible = false;
                        motivo = "Reservado";
                    } else {
                        // 3. Evaluar si existe un lock Redis activo
                        if (redisLockService.hasLock(canchaId, fecha, start)) {
                            disponible = false;
                            motivo = "En proceso de pago";
                        }
                    }
                }
            }

            return new SlotDisponibilidad(
                    f.getId(),
                    start,
                    end,
                    f.getDuracionMin(),
                    precio,
                    disponible,
                    motivo
            );
        }).toList();

        return new DisponibilidadResponse(
                canchaId,
                cancha.getNombre(),
                fecha,
                slots
        );
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
