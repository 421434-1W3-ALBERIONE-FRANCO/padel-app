package com.padel.service;

import com.padel.dto.request.CrearBonoRequest;
import com.padel.dto.response.BonoResponse;
import com.padel.dto.response.PagoResponse;

import java.util.List;

public interface BonoService {
    BonoResponse asignarBono(CrearBonoRequest request);
    List<BonoResponse> obtenerMisBonos(String usuarioEmail);
    BonoResponse obtenerPorId(Long id, String usuarioEmail);
    PagoResponse usarBono(Long reservaId, String usuarioEmail);
    void expirarBonosVencidos();
}
