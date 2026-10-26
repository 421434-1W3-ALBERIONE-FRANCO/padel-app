package com.padel.service.impl;

import com.padel.domain.entity.BloqueoCancha;
import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.Reserva;
import com.padel.domain.evento.DisponibilidadCambiadaEvent;
import com.padel.domain.valueobject.RangoHorario;
import com.padel.dto.request.BloqueoCanchaRequest;
import com.padel.dto.response.BloqueoCanchaResponse;
import com.padel.exception.RangoHorarioInvalidoException;
import com.padel.exception.ReservasAfectadasException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.exception.SolapamientoBloqueoException;
import com.padel.mapper.BloqueoCanchaMapper;
import com.padel.repository.BloqueoCanchaRepository;
import com.padel.repository.CanchaRepository;
import com.padel.repository.ReservaRepository;
import com.padel.service.BloqueoCanchaService;
import com.padel.service.ReservaService;
import com.padel.util.FechaHoraUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BloqueoCanchaServiceImpl implements BloqueoCanchaService {

    private final BloqueoCanchaRepository bloqueoCanchaRepository;
    private final CanchaRepository canchaRepository;
    private final BloqueoCanchaMapper bloqueoCanchaMapper;
    private final ReservaRepository reservaRepository;
    private final ReservaService reservaService;
    private final ApplicationEventPublisher eventos;
    private final FechaHoraUtil fechaHoraUtil;

    @Override
    public BloqueoCanchaResponse crear(Long canchaId, BloqueoCanchaRequest request) {
        Cancha cancha = canchaRepository.findById(canchaId)
                .filter(Cancha::isActiva)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha no encontrada o inactiva"));

        validarRangos(request.fechaDesde(), request.fechaHasta(), request.horaDesde(), request.horaHasta());
        validarSolapamientos(canchaId, request.fechaDesde(), request.fechaHasta(), request.horaDesde(), request.horaHasta(), null);
        cancelarReservasAfectadas(canchaId, request);

        BloqueoCancha bloqueo = bloqueoCanchaMapper.toEntity(request);
        bloqueo.setCancha(cancha);

        BloqueoCancha guardado = bloqueoCanchaRepository.save(bloqueo);
        notificarCambioDisponibilidad(canchaId);

        return bloqueoCanchaMapper.toResponse(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BloqueoCanchaResponse> obtenerPorCanchaId(Long canchaId) {
        if (!canchaRepository.existsById(canchaId)) {
            throw new ResourceNotFoundException("Cancha no encontrada");
        }
        return bloqueoCanchaRepository.findByCanchaId(canchaId).stream()
                .map(bloqueoCanchaMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BloqueoCanchaResponse obtenerPorId(Long id) {
        BloqueoCancha bloqueo = bloqueoCanchaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueo no encontrado"));
        return bloqueoCanchaMapper.toResponse(bloqueo);
    }

    @Override
    public BloqueoCanchaResponse actualizar(Long id, BloqueoCanchaRequest request) {
        BloqueoCancha bloqueoExistente = bloqueoCanchaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueo no encontrado"));

        Long canchaId = bloqueoExistente.getCancha().getId();
        validarRangos(request.fechaDesde(), request.fechaHasta(), request.horaDesde(), request.horaHasta());
        validarSolapamientos(canchaId, request.fechaDesde(), request.fechaHasta(), request.horaDesde(), request.horaHasta(), id);
        cancelarReservasAfectadas(canchaId, request);

        bloqueoExistente.setFechaDesde(request.fechaDesde());
        bloqueoExistente.setFechaHasta(request.fechaHasta());
        bloqueoExistente.setHoraDesde(request.horaDesde());
        bloqueoExistente.setHoraHasta(request.horaHasta());
        bloqueoExistente.setMotivo(request.motivo());

        BloqueoCancha guardado = bloqueoCanchaRepository.save(bloqueoExistente);
        notificarCambioDisponibilidad(canchaId);

        return bloqueoCanchaMapper.toResponse(guardado);
    }

    @Override
    public void eliminar(Long id) {
        BloqueoCancha bloqueo = bloqueoCanchaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueo no encontrado"));

        Long canchaId = bloqueo.getCancha().getId();
        bloqueoCanchaRepository.delete(bloqueo);
        notificarCambioDisponibilidad(canchaId);
    }

    private void validarRangos(LocalDate fechaDesde, LocalDate fechaHasta, LocalTime horaDesde, LocalTime horaHasta) {
        fechaHoraUtil.validarAnioRazonable(fechaDesde, "la fecha de inicio del bloqueo");
        fechaHoraUtil.validarAnioRazonable(fechaHasta, "la fecha de fin del bloqueo");
        if (fechaDesde.isAfter(fechaHasta)) {
            throw new RangoHorarioInvalidoException("La fecha de inicio del bloqueo no puede ser posterior a la fecha de fin");
        }
        if (!horaDesde.isBefore(horaHasta)) {
            throw new RangoHorarioInvalidoException("La hora de inicio del bloqueo debe ser anterior a la hora de fin");
        }
    }

    private void validarSolapamientos(Long canchaId, LocalDate fechaDesde, LocalDate fechaHasta, LocalTime horaDesde, LocalTime horaHasta, Long id) {
        List<BloqueoCancha> overlapping = bloqueoCanchaRepository.findOverlappingBlocks(
                canchaId, fechaDesde, fechaHasta, horaDesde, horaHasta, id);
        if (!overlapping.isEmpty()) {
            throw new SolapamientoBloqueoException("La cancha ya se encuentra bloqueada en el rango de fecha y hora solicitado");
        }
    }

    private void notificarCambioDisponibilidad(Long canchaId) {
        eventos.publishEvent(new DisponibilidadCambiadaEvent(canchaId));
    }

    /**
     * Un bloqueo (lluvia, mantenimiento, torneo) que pisa reservas vigentes ya no las deja en el aire: hay que
     * confirmarlo y, al hacerlo, se cancelan como cancelación del club (con reintegro y aviso al jugador).
     */
    private void cancelarReservasAfectadas(Long canchaId, BloqueoCanchaRequest request) {
        RangoHorario rangoBloqueo = new RangoHorario(request.horaDesde(), request.horaHasta());
        List<Reserva> afectadas = reservaRepository.findVigentesEntreFechas(canchaId, request.fechaDesde(), request.fechaHasta())
                .stream()
                .filter(r -> rangoBloqueo.seSuperponeCon(r.getHoraInicio(), r.getHoraFin()))
                .toList();
        if (afectadas.isEmpty()) {
            return;
        }
        if (!request.confirmaCancelarReservas()) {
            throw new ReservasAfectadasException(afectadas.size() == 1
                    ? "Hay 1 reserva vigente en ese horario. Confirmá el bloqueo para cancelarla y avisarle al jugador."
                    : "Hay " + afectadas.size() + " reservas vigentes en ese horario. Confirmá el bloqueo para cancelarlas y avisar a los jugadores.");
        }
        afectadas.forEach(r -> reservaService.cancelarPorClub(r.getId(), "Cancha no disponible: " + request.motivo()));
    }
}
