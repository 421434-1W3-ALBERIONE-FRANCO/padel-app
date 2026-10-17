package com.padel.service;

import com.padel.dto.request.PerfilJugadorRequest;
import com.padel.dto.response.PerfilJugadorResponse;

public interface PerfilJugadorService {
    PerfilJugadorResponse obtenerMiPerfil(String email);
    PerfilJugadorResponse guardarMiPerfil(PerfilJugadorRequest request, String email);
}
