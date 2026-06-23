package com.padel.service;

import com.padel.dto.request.BloqueoCanchaRequest;
import com.padel.dto.response.BloqueoCanchaResponse;

import java.util.List;

public interface BloqueoCanchaService {
    BloqueoCanchaResponse crear(Long canchaId, BloqueoCanchaRequest request);
    List<BloqueoCanchaResponse> obtenerPorCanchaId(Long canchaId);
    BloqueoCanchaResponse obtenerPorId(Long id);
    BloqueoCanchaResponse actualizar(Long id, BloqueoCanchaRequest request);
    void eliminar(Long id);
}
