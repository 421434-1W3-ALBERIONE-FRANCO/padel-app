package com.padel.dto.request;

import com.padel.domain.enums.OrigenReserva;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReservaRequest(
    @NotNull(message = "El ID de la cancha es obligatorio")
    Long canchaId,

    @NotNull(message = "El ID de la franja horaria es obligatorio")
    Long franjaId,

    @NotNull(message = "La fecha es obligatoria")
    @FutureOrPresent(message = "La fecha de reserva debe ser en el presente o futuro")
    LocalDate fecha,

    @NotNull(message = "El origen de la reserva es obligatorio")
    OrigenReserva origen
) {}
