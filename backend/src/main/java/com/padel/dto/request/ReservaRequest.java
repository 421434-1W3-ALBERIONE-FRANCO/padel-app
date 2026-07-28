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
    OrigenReserva origen,

    // Duración en minutos. Opcional: si es null, se usa la duración de la franja (turno estándar).
    Integer duracionMin,

    // Cliente para el que se crea la reserva (alta rápida por staff/admin). Opcional:
    // si es null, la reserva es para el usuario autenticado.
    Long usuarioId
) {}
