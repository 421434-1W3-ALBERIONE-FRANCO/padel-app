package com.padel.service.impl;

import com.padel.domain.entity.TurnoEmpleado;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.TurnoRequest;
import com.padel.dto.response.TurnoResponse;
import com.padel.exception.DatosInvalidosException;
import com.padel.exception.RangoHorarioInvalidoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.TurnoEmpleadoMapper;
import com.padel.repository.TurnoEmpleadoRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.TurnoEmpleadoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TurnoEmpleadoServiceImpl implements TurnoEmpleadoService {

    private final TurnoEmpleadoRepository turnoEmpleadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TurnoEmpleadoMapper turnoEmpleadoMapper;

    @Override
    @Transactional
    public TurnoResponse crear(TurnoRequest request) {
        Usuario empleado = obtenerEmpleadoValido(request.empleadoEmail());
        validarRango(request);

        turnoEmpleadoRepository.findByEmpleadoIdAndDiaSemana(empleado.getId(), request.diaSemana())
                .ifPresent(t -> {
                    throw new DatosInvalidosException("El empleado ya tiene un turno asignado para " + request.diaSemana());
                });

        TurnoEmpleado turno = TurnoEmpleado.builder()
                .empleado(empleado)
                .diaSemana(request.diaSemana())
                .horaInicio(request.horaInicio())
                .horaFin(request.horaFin())
                .build();

        return turnoEmpleadoMapper.toResponse(turnoEmpleadoRepository.save(turno));
    }

    @Override
    @Transactional
    public TurnoResponse actualizar(Long id, TurnoRequest request) {
        TurnoEmpleado turno = turnoEmpleadoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Turno no encontrado con ID: " + id));
        Usuario empleado = obtenerEmpleadoValido(request.empleadoEmail());
        validarRango(request);

        turnoEmpleadoRepository.findByEmpleadoIdAndDiaSemana(empleado.getId(), request.diaSemana())
                .filter(t -> !t.getId().equals(id))
                .ifPresent(t -> {
                    throw new DatosInvalidosException("El empleado ya tiene un turno asignado para " + request.diaSemana());
                });

        turno.setEmpleado(empleado);
        turno.setDiaSemana(request.diaSemana());
        turno.setHoraInicio(request.horaInicio());
        turno.setHoraFin(request.horaFin());

        return turnoEmpleadoMapper.toResponse(turnoEmpleadoRepository.save(turno));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        if (!turnoEmpleadoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Turno no encontrado con ID: " + id);
        }
        turnoEmpleadoRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurnoResponse> listarPorEmpleado(Long empleadoId) {
        return turnoEmpleadoRepository.findByEmpleadoId(empleadoId).stream()
                .map(turnoEmpleadoMapper::toResponse)
                .toList();
    }

    private Usuario obtenerEmpleadoValido(String email) {
        Usuario empleado = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
        // El control horario solo aplica al staff: nunca a un JUGADOR.
        if (empleado.getRol() != RolUsuario.ADMIN && empleado.getRol() != RolUsuario.RECEPCIONISTA) {
            throw new DatosInvalidosException("Solo se pueden asignar turnos a personal ADMIN o RECEPCIONISTA");
        }
        return empleado;
    }

    private void validarRango(TurnoRequest request) {
        if (!request.horaInicio().isBefore(request.horaFin())) {
            throw new RangoHorarioInvalidoException("La hora de inicio debe ser anterior a la hora de fin");
        }
    }
}
