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
    // Para los jugadores del partido (el creador y los aceptados) mientras la solicitud no se cancele; null para el resto.
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
    // El creador ve todas las postulaciones; un aceptado, la suya y las de los otros aceptados; cualquier otro, solo la suya.
    List<PostulacionSolicitudResponse> postulaciones,
    long cantidadPostulaciones
) {}
