package com.padel.service;

import com.padel.dto.request.TurnoRequest;
import com.padel.dto.response.TurnoResponse;

import java.util.List;

public interface TurnoEmpleadoService {
    TurnoResponse crear(TurnoRequest request);
    TurnoResponse actualizar(Long id, TurnoRequest request);
    void eliminar(Long id);
    List<TurnoResponse> listarPorEmpleado(Long empleadoId);
}
