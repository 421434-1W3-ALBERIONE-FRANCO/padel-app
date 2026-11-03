package com.padel.service;

import com.padel.dto.request.PerfilJugadorRequest;
import com.padel.dto.response.FotoPerfilArchivo;
import com.padel.dto.response.PerfilJugadorResponse;

public interface PerfilJugadorService {
    PerfilJugadorResponse obtenerMiPerfil(String email);
    PerfilJugadorResponse guardarMiPerfil(PerfilJugadorRequest request, String email);
    PerfilJugadorResponse guardarMiFoto(byte[] imagen, String email);
    PerfilJugadorResponse eliminarMiFoto(String email);
    FotoPerfilArchivo obtenerFoto(Long usuarioId, String emailQuienConsulta);
}
