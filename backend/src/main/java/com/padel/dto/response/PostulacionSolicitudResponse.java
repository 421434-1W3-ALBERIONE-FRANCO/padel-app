package com.padel.dto.response;

import com.padel.domain.enums.EstadoPostulacion;

import java.time.LocalDateTime;

public record PostulacionSolicitudResponse(
    Long id,
    Long solicitudId,
    Long jugadorId,
    String jugadorNombre,
    // Solo cuando la ve el creador de la solicitud y la postulación está aceptada.
    ContactoResponse contacto,
    String mensaje,
    EstadoPostulacion estado,
    LocalDateTime createdAt
) {}
