package com.padel.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReservaMatchRequest(
    @NotNull(message = "El ID de la cancha es obligatorio")
    Long canchaId,

    @NotNull(message = "El ID de la franja horaria es obligatorio")
    Long franjaId,

    @NotNull(message = "La fecha es obligatoria")
    @FutureOrPresent(message = "La fecha de reserva debe ser en el presente o futuro")
    LocalDate fecha,

    // Opcional: si falta, se usa la duración de la franja (turno estándar).
    Integer duracionMin
) {}
