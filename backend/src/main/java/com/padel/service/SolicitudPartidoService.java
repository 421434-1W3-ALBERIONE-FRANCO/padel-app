package com.padel.service;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoSolicitud;
import com.padel.domain.enums.TipoSolicitud;
import com.padel.dto.request.PostulacionSolicitudRequest;
import com.padel.dto.request.SolicitudPartidoRequest;
import com.padel.dto.response.SolicitudPartidoResponse;

import java.util.List;

public interface SolicitudPartidoService {
    SolicitudPartidoResponse crear(SolicitudPartidoRequest request, String creadorEmail);
    List<SolicitudPartidoResponse> buscar(TipoSolicitud tipoSolicitud, CategoriaJugador categoria, EstadoSolicitud estado);
    SolicitudPartidoResponse obtenerPorId(Long id);
    void cancelar(Long id, String usuarioEmail);

    SolicitudPartidoResponse postularse(Long solicitudId, PostulacionSolicitudRequest request, String jugadorEmail);
    SolicitudPartidoResponse aceptarPostulacion(Long solicitudId, Long postulacionId, String creadorEmail);
    SolicitudPartidoResponse rechazarPostulacion(Long solicitudId, Long postulacionId, String creadorEmail);

    List<SolicitudPartidoResponse> misSolicitudes(String usuarioEmail);
    List<SolicitudPartidoResponse> misPostulaciones(String usuarioEmail);
}
