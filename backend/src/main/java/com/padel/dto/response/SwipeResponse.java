package com.padel.dto.response;

/** Resultado de responder una tarjeta: si fue match mutuo, trae la pareja con su contacto. */
public record SwipeResponse(
    boolean match,
    MatchParejaResponse pareja
) {}
