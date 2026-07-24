package com.padel.service;

import com.padel.dto.request.LiquidacionRequest;
import com.padel.dto.response.LiquidacionResponse;

import java.util.List;

public interface LiquidacionService {
    LiquidacionResponse liquidar(LiquidacionRequest request, String adminEmail);
    List<LiquidacionResponse> historialPorEmpleado(Long empleadoId);
}
