package com.padel.service;

import com.padel.dto.response.PagoResponse;
import com.padel.dto.response.PreferenciaResponse;

public interface PagoService {
    PreferenciaResponse crearPreferencia(Long reservaId, String usuarioEmail);
    void procesarWebhook(String type, String paymentId);
    PagoResponse obtenerPorId(Long id, String usuarioEmail);
}
