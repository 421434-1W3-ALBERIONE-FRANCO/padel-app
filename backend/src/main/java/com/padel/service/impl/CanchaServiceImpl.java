package com.padel.service.impl;

import com.padel.domain.entity.Cancha;
import com.padel.dto.request.CanchaRequest;
import com.padel.dto.response.CanchaResponse;
import com.padel.exception.NombreDuplicadoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.CanchaMapper;
import com.padel.repository.CanchaRepository;
import com.padel.service.CanchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CanchaServiceImpl implements CanchaService {

    private final CanchaRepository canchaRepository;
    private final CanchaMapper canchaMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public CanchaResponse crear(CanchaRequest request) {
        if (canchaRepository.existsByNombreAndActivaTrue(request.nombre())) {
            throw new NombreDuplicadoException("Ya existe una cancha activa con el nombre: " + request.nombre());
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

        if (canchaRepository.existsByNombreAndActivaTrueAndIdNot(request.nombre(), id)) {
            throw new NombreDuplicadoException("Ya existe otra cancha activa con el nombre: " + request.nombre());
        }

        cancha.setNombre(request.nombre());
        cancha.setTipoSuelo(request.tipoSuelo());
        cancha.setTechada(request.techada());
        cancha.setTieneLuz(request.tieneLuz());

        Cancha guardada = canchaRepository.save(cancha);
        notificarCambio(id);
        return canchaMapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Cancha cancha = canchaRepository.findByIdAndActivaTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha no encontrada o inactiva con id: " + id));

        cancha.setActiva(false);
        canchaRepository.save(cancha);
        notificarCambio(id);
    }

    private void notificarCambio(Long canchaId) {
        try {
            messagingTemplate.convertAndSend("/topic/disponibilidad", Map.of(
                    "type", "court_update",
                    "canchaId", canchaId
            ));
        } catch (Exception e) {
            // Ignorar en tests
        }
    }
}
