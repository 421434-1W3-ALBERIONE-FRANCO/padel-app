package com.padel.dto.response;

import com.padel.domain.enums.EstadoPostulacion;

import java.time.LocalDateTime;

public record PostulacionSolicitudResponse(
    Long id,
    Long solicitudId,
    Long jugadorId,
    String jugadorNombre,
    // Si la postulación está aceptada, para los otros jugadores del partido (el creador y los demás aceptados)
    // mientras la solicitud no se cancele.
    ContactoResponse contacto,
    String mensaje,
    EstadoPostulacion estado,
    LocalDateTime createdAt
) {}
