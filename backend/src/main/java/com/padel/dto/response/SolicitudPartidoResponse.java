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
    // Solo para los postulantes aceptados (y el propio creador); null para el resto.
    ContactoResponse creadorContacto,
    TipoSolicitud tipoSolicitud,
    CategoriaJugador categoria,
    Integer cantidadJugadoresFaltantes,
    LocalDateTime fechaHoraPropuesta,
    Long canchaId,
    String canchaNombre,
    String descripcion,
    EstadoSolicitud estado,
    LocalDateTime createdAt,
    // El creador ve todas las postulaciones; cualquier otro jugador, solo la suya.
    List<PostulacionSolicitudResponse> postulaciones,
    long cantidadPostulaciones
) {}
