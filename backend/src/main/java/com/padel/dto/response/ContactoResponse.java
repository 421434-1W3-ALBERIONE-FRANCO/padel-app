package com.padel.dto.response;

/**
 * Contacto de otro jugador. Solo se incluye cuando ya hay un acuerdo entre las partes (postulación aceptada,
 * match mutuo): antes el tablón, el ranking y las inscripciones mostraban el email de todos a cualquiera.
 */
public record ContactoResponse(
    String email,
    String telefono
) {}
