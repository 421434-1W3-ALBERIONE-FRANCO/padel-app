package com.padel.service;

import com.padel.dto.request.FranjaHorariaRequest;
import com.padel.dto.response.FranjaHorariaResponse;

import java.util.List;

public interface FranjaHorariaService {
    FranjaHorariaResponse crear(Long canchaId, FranjaHorariaRequest request);
    List<FranjaHorariaResponse> obtenerPorCanchaId(Long canchaId);
    FranjaHorariaResponse obtenerPorId(Long id);
    FranjaHorariaResponse actualizar(Long id, FranjaHorariaRequest request);
    void eliminar(Long id);
}
