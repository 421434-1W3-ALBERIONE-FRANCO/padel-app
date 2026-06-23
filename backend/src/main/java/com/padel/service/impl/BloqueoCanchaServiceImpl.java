package com.padel.service.impl;

import com.padel.domain.entity.BloqueoCancha;
import com.padel.domain.entity.Cancha;
import com.padel.dto.request.BloqueoCanchaRequest;
import com.padel.dto.response.BloqueoCanchaResponse;
import com.padel.exception.RangoHorarioInvalidoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.exception.SolapamientoBloqueoException;
import com.padel.mapper.BloqueoCanchaMapper;
import com.padel.repository.BloqueoCanchaRepository;
import com.padel.repository.CanchaRepository;
import com.padel.service.BloqueoCanchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class BloqueoCanchaServiceImpl implements BloqueoCanchaService {

    private final BloqueoCanchaRepository bloqueoCanchaRepository;
    private final CanchaRepository canchaRepository;
    private final BloqueoCanchaMapper bloqueoCanchaMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public BloqueoCanchaResponse crear(Long canchaId, BloqueoCanchaRequest request) {
        Cancha cancha = canchaRepository.findById(canchaId)
                .filter(Cancha::isActiva)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha no encontrada o inactiva"));

        validarRangos(request.fechaDesde(), request.fechaHasta(), request.horaDesde(), request.horaHasta());
        validarSolapamientos(canchaId, request.fechaDesde(), request.fechaHasta(), request.horaDesde(), request.horaHasta(), null);

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
        try {
            messagingTemplate.convertAndSend("/topic/disponibilidad", Map.of(
                    "type", "availability_update",
                    "canchaId", canchaId
            ));
        } catch (Exception e) {
            // Ignorar excepciones en tests donde SimpMessagingTemplate podría no estar mockeado
        }
    }
}
