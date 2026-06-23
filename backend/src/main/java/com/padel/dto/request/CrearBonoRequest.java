package com.padel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CrearBonoRequest(
    @NotBlank(message = "El email de usuario es obligatorio") String usuarioEmail,
    @NotBlank(message = "El tipo de bono es obligatorio") String tipo,
    @NotNull(message = "Las horas totales son obligatorias") Integer horasTotales,
    @NotNull(message = "El precio pagado es obligatorio") BigDecimal precioPagado,
    @NotNull(message = "La fecha de vencimiento es obligatoria") LocalDate fechaVencimiento
) {}
