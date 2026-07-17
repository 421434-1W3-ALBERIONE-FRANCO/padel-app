package com.padel.dto.response;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoSolicitud;
import com.padel.domain.enums.TipoSolicitud;

import java.time.LocalDateTime;
import java.util.List;

public record SolicitudPartidoResponse(
    Long id,
    Long creadorId,
    String creadorNombre,
    String creadorEmail,
    TipoSolicitud tipoSolicitud,
    CategoriaJugador categoria,
    Integer cantidadJugadoresFaltantes,
    LocalDateTime fechaHoraPropuesta,
    Long canchaId,
    String canchaNombre,
    String descripcion,
    EstadoSolicitud estado,
    LocalDateTime createdAt,
    List<PostulacionSolicitudResponse> postulaciones
) {}
