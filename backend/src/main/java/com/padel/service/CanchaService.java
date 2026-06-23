package com.padel.service;

import com.padel.dto.request.CanchaRequest;
import com.padel.dto.response.CanchaResponse;

import java.util.List;

public interface CanchaService {
    CanchaResponse crear(CanchaRequest request);
    List<CanchaResponse> obtenerTodasActivas();
    CanchaResponse obtenerActivaPorId(Long id);
    CanchaResponse actualizar(Long id, CanchaRequest request);
    void desactivar(Long id);
}
