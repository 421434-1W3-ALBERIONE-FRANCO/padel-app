package com.padel.service.impl;

import com.padel.domain.entity.BloqueoCancha;
import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.domain.entity.Reserva;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.valueobject.RangoHorario;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisponibilidadServiceImpl implements DisponibilidadService {

    private final CanchaRepository canchaRepository;
    private final FranjaHorariaRepository franjaHorariaRepository;
    private final BloqueoCanchaRepository bloqueoCanchaRepository;
    private final ReservaRepository reservaRepository;
    private final RedisLockService redisLockService;
    private final CalculadoraPrecioReserva calculadoraPrecio;

    @Override
    public DisponibilidadResponse obtenerDisponibilidad(Long canchaId, LocalDate fecha) {
        Cancha cancha = canchaRepository.findById(canchaId)
                .filter(Cancha::isActiva)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha no encontrada o inactiva"));

        List<FranjaHoraria> franjasDelDia = calculadoraPrecio.franjasDelDia(
                franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(canchaId), fecha.getDayOfWeek());
        List<BloqueoCancha> bloqueos = bloqueoCanchaRepository.findBlocksForDate(canchaId, fecha);
        List<Reserva> reservas = reservaRepository.findActiveReservationsByCanchaAndDate(canchaId, fecha);
        Set<Integer> duraciones = cancha.duracionesPermitidasSet();

        LocalDate hoy = LocalDate.now();
        LocalTime ahora = LocalTime.now();

        List<SlotDisponibilidad> slots = franjasDelDia.stream().map(f -> {
            RangoHorario rangoFranja = new RangoHorario(f.getHoraInicio(), f.getHoraFin());
            String motivo = motivoNoDisponible(canchaId, fecha, hoy, ahora, rangoFranja, bloqueos, reservas);
            boolean disponible = motivo == null;
            List<OpcionDuracion> opciones = disponible
                    ? calcularOpciones(f.getHoraInicio(), franjasDelDia, reservas, bloqueos, duraciones)
                    : List.of();
            return new SlotDisponibilidad(f.getId(), f.getHoraInicio(), f.getHoraFin(), f.getDuracionMin(),
                    calculadoraPrecio.tarifa(f), disponible, motivo, opciones);
        }).toList();

        return new DisponibilidadResponse(canchaId, cancha.getNombre(), fecha, slots);
    }

    private String motivoNoDisponible(Long canchaId, LocalDate fecha, LocalDate hoy, LocalTime ahora, RangoHorario rango,
                                      List<BloqueoCancha> bloqueos, List<Reserva> reservas) {
        if (fecha.isBefore(hoy)) {
            return "Fecha pasada";
        }
        if (fecha.equals(hoy) && rango.inicio().isBefore(ahora)) {
            return "Horario pasado";
        }
        Optional<BloqueoCancha> bloqueo = bloqueos.stream()
                .filter(b -> rango.seSuperponeCon(b.getHoraDesde(), b.getHoraHasta()))
                .findFirst();
        if (bloqueo.isPresent()) {
            return bloqueo.get().getMotivo();
        }
        Optional<Reserva> reserva = reservas.stream()
                .filter(r -> rango.seSuperponeCon(r.getHoraInicio(), r.getHoraFin()))
                .findFirst();
        if (reserva.isPresent()) {
            return reserva.get().getEstadoReserva() == EstadoReserva.PENDIENTE_PAGO ? "En proceso de pago" : "Reservado";
        }
        if (redisLockService.hasLock(canchaId, fecha, rango.inicio())) {
            return "En proceso de pago";
        }
        return null;
    }

    // Para un inicio dado, las duraciones permitidas que entran en la ventana libre contigua (limitada por el
    // fin del horario de apertura, la próxima reserva y el próximo bloqueo), cada una con su precio prorrateado.
    private List<OpcionDuracion> calcularOpciones(LocalTime inicio, List<FranjaHoraria> franjasDelDia,
                                                  List<Reserva> reservas, List<BloqueoCancha> bloqueos,
                                                  Set<Integer> duraciones) {
        LocalTime limite = calculadoraPrecio.finDeAperturaContigua(franjasDelDia, inicio);
        for (Reserva r : reservas) {
            if (!r.getHoraInicio().isBefore(inicio) && r.getHoraInicio().isBefore(limite)) {
                limite = r.getHoraInicio();
            }
        }
        for (BloqueoCancha b : bloqueos) {
            if (!b.getHoraDesde().isBefore(inicio) && b.getHoraDesde().isBefore(limite)) {
                limite = b.getHoraDesde();
            }
        }

        List<OpcionDuracion> opciones = new ArrayList<>();
        for (Integer duracion : duraciones) {
            if (!RangoHorario.cabeEnElDia(inicio, duracion)) {
                continue; // pasaría la medianoche
            }
            RangoHorario rango = RangoHorario.desde(inicio, duracion);
            if (rango.fin().isAfter(limite)) {
                continue; // no entra en la ventana libre
            }
            calculadoraPrecio.calcular(franjasDelDia, rango)
                    .ifPresent(precio -> opciones.add(new OpcionDuracion(duracion, rango.fin(), precio)));
        }
        return opciones;
    }
}
