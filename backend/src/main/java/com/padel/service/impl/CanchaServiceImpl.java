package com.padel.service.impl;

import com.padel.domain.entity.Cancha;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.evento.DisponibilidadCambiadaEvent;
import com.padel.dto.request.CanchaRequest;
import com.padel.dto.response.CanchaResponse;
import com.padel.exception.EstadoInvalidoException;
import com.padel.exception.NombreDuplicadoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.CanchaMapper;
import com.padel.repository.CanchaRepository;
import com.padel.repository.ReservaRepository;
import com.padel.service.CanchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CanchaServiceImpl implements CanchaService {

    private final CanchaRepository canchaRepository;
    private final ReservaRepository reservaRepository;
    private final CanchaMapper canchaMapper;
    private final ApplicationEventPublisher eventos;

    @Override
    @Transactional
    public CanchaResponse crear(CanchaRequest request) {
        if (canchaRepository.existsByNombre(request.nombre())) {
            throw new NombreDuplicadoException("Ya existe una cancha (activa o dada de baja) con el nombre: " + request.nombre());
        }

        Cancha cancha = canchaMapper.toEntity(request);
        cancha.setActiva(true);

        Cancha guardada = canchaRepository.save(cancha);
        notificarCambio(guardada.getId());
        return canchaMapper.toResponse(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CanchaResponse> obtenerTodasActivas() {
        return canchaRepository.findByActivaTrue().stream()
                .map(canchaMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CanchaResponse obtenerActivaPorId(Long id) {
        Cancha cancha = canchaRepository.findByIdAndActivaTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha no encontrada o inactiva con id: " + id));
        return canchaMapper.toResponse(cancha);
    }

    @Override
    @Transactional
    public CanchaResponse actualizar(Long id, CanchaRequest request) {
        Cancha cancha = canchaRepository.findByIdAndActivaTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha no encontrada o inactiva con id: " + id));

        if (canchaRepository.existsByNombreAndIdNot(request.nombre(), id)) {
            throw new NombreDuplicadoException("Ya existe otra cancha (activa o dada de baja) con el nombre: " + request.nombre());
        }

        cancha.setNombre(request.nombre());
        cancha.setTipoSuelo(request.tipoSuelo());
        cancha.setTechada(request.techada());
        cancha.setTieneLuz(request.tieneLuz());
        cancha.setDuracionesPermitidas(canchaMapper.duracionesToString(request.duracionesPermitidas()));

        Cancha guardada = canchaRepository.save(cancha);
        notificarCambio(id);
        return canchaMapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Cancha cancha = canchaRepository.findByIdAndActivaTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha no encontrada o inactiva con id: " + id));

        // Dar de baja una cancha con turnos vendidos los dejaba en una cancha inexistente para el jugador.
        if (reservaRepository.existsByCanchaIdAndFechaGreaterThanEqualAndEstadoReservaIn(
                id, LocalDate.now(), List.of(EstadoReserva.PENDIENTE_PAGO, EstadoReserva.CONFIRMADA))) {
            throw new EstadoInvalidoException("La cancha tiene reservas vigentes. Bloqueala para cancelarlas y avisar a los jugadores antes de darla de baja.");
        }
        cancha.setActiva(false);
        canchaRepository.save(cancha);
        notificarCambio(id);
    }

    private void notificarCambio(Long canchaId) {
        eventos.publishEvent(new DisponibilidadCambiadaEvent(canchaId));
    }
}
