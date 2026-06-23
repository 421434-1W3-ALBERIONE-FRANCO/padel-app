package com.padel.service.impl;

import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.dto.request.FranjaHorariaRequest;
import com.padel.dto.response.FranjaHorariaResponse;
import com.padel.exception.RangoHorarioInvalidoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.exception.SolapamientoHorarioException;
import com.padel.mapper.FranjaHorariaMapper;
import com.padel.repository.CanchaRepository;
import com.padel.repository.FranjaHorariaRepository;
import com.padel.service.FranjaHorariaService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FranjaHorariaServiceImpl implements FranjaHorariaService {

    private final FranjaHorariaRepository franjaHorariaRepository;
    private final CanchaRepository canchaRepository;
    private final FranjaHorariaMapper franjaHorariaMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public FranjaHorariaResponse crear(Long canchaId, FranjaHorariaRequest request) {
        Cancha cancha = canchaRepository.findByIdAndActivaTrue(canchaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha no encontrada o inactiva con id: " + canchaId));

        validarRangoHorario(request);
        validarSolapamientos(canchaId, request, null);

        FranjaHoraria franja = franjaHorariaMapper.toEntity(request);
        franja.setCancha(cancha);
        franja.setDuracionMin((int) Duration.between(request.horaInicio(), request.horaFin()).toMinutes());

        FranjaHoraria guardada = franjaHorariaRepository.save(franja);
        notificarCambio(canchaId);
        return franjaHorariaMapper.toResponse(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FranjaHorariaResponse> obtenerPorCanchaId(Long canchaId) {
        if (!canchaRepository.existsById(canchaId)) {
            throw new ResourceNotFoundException("Cancha no encontrada con id: " + canchaId);
        }
        return franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(canchaId).stream()
                .map(franjaHorariaMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FranjaHorariaResponse obtenerPorId(Long id) {
        FranjaHoraria franja = obtenerEntidadValida(id);
        return franjaHorariaMapper.toResponse(franja);
    }

    @Override
    @Transactional
    public FranjaHorariaResponse actualizar(Long id, FranjaHorariaRequest request) {
        FranjaHoraria franja = obtenerEntidadValida(id);

        validarRangoHorario(request);
        validarSolapamientos(franja.getCancha().getId(), request, id);

        franja.setHoraInicio(request.horaInicio());
        franja.setHoraFin(request.horaFin());
        franja.setDuracionMin((int) Duration.between(request.horaInicio(), request.horaFin()).toMinutes());
        franja.setPrecioBase(request.precioBase());
        franja.setPrecioNocturno(request.precioNocturno());
        franja.setDiasAplicables(franjaHorariaMapper.daysToString(request.diasAplicables()));

        FranjaHoraria guardada = franjaHorariaRepository.save(franja);
        notificarCambio(franja.getCancha().getId());
        return franjaHorariaMapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        FranjaHoraria franja = obtenerEntidadValida(id);
        Long canchaId = franja.getCancha().getId();
        franjaHorariaRepository.delete(franja);
        notificarCambio(canchaId);
    }

    private void notificarCambio(Long canchaId) {
        try {
            messagingTemplate.convertAndSend("/topic/disponibilidad", Map.of(
                    "type", "slot_update",
                    "canchaId", canchaId
            ));
        } catch (Exception e) {
            // Ignorar en tests
        }
    }

    private FranjaHoraria obtenerEntidadValida(Long id) {
        FranjaHoraria franja = franjaHorariaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Franja horaria no encontrada con id: " + id));
        if (!franja.getCancha().isActiva()) {
            throw new ResourceNotFoundException("Franja horaria no encontrada (cancha asociada inactiva)");
        }
        return franja;
    }

    private void validarRangoHorario(FranjaHorariaRequest request) {
        if (!request.horaInicio().isBefore(request.horaFin())) {
            throw new RangoHorarioInvalidoException("La hora de inicio (" + request.horaInicio() + ") debe ser anterior a la hora de fin (" + request.horaFin() + ").");
        }
    }

    private void validarSolapamientos(Long canchaId, FranjaHorariaRequest request, Long excludeId) {
        List<FranjaHoraria> existentes = franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(canchaId);

        for (FranjaHoraria existente : existentes) {
            // Excluir la misma franja al actualizar
            if (excludeId != null && existente.getId().equals(excludeId)) {
                continue;
            }

            Set<DayOfWeek> diasExistentes = parseDiasAplicables(existente.getDiasAplicables());
            
            // Buscar si comparten algún día de la semana
            boolean comparteDias = request.diasAplicables().stream().anyMatch(diasExistentes::contains);

            if (comparteDias) {
                // Verificar solapamiento de horas: (Inicio1 < Fin2) && (Inicio2 < Fin1)
                boolean seSolapan = request.horaInicio().isBefore(existente.getHoraFin()) &&
                        existente.getHoraInicio().isBefore(request.horaFin());

                if (seSolapan) {
                    throw new SolapamientoHorarioException(
                            String.format("El horario propuesto (%s - %s) se solapa con una franja existente (%s - %s) en los mismos días.",
                                    request.horaInicio(), request.horaFin(), existente.getHoraInicio(), existente.getHoraFin())
                    );
                }
            }
        }
    }

    private Set<DayOfWeek> parseDiasAplicables(String diasString) {
        if (diasString == null || diasString.trim().isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(diasString.split(","))
                .map(String::trim)
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
    }
}
