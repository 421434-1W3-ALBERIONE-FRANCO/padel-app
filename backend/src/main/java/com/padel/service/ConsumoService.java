package com.padel.service;

import com.padel.dto.request.CerrarCuentaRequest;
import com.padel.dto.request.ConsumoRequest;
import com.padel.dto.response.ConsumoResponse;

import java.util.List;

public interface ConsumoService {
    ConsumoResponse cargarConsumo(Long reservaId, ConsumoRequest request);
    List<ConsumoResponse> obtenerConsumosPorReserva(Long reservaId, String usuarioEmail);
    void cerrarCuenta(Long reservaId, CerrarCuentaRequest request);
    void marcarConsumosComoPagados(Long pagoId);
}
