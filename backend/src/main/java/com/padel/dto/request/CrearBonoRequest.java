package com.padel.dto.request;

import com.padel.domain.enums.MetodoPago;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CrearBonoRequest(
    @NotBlank(message = "El email de usuario es obligatorio") String usuarioEmail,
    @NotBlank(message = "El tipo de bono es obligatorio") String tipo,
    @NotNull(message = "Las horas totales son obligatorias") Integer horasTotales,
    @NotNull(message = "El precio pagado es obligatorio")
    @PositiveOrZero(message = "El precio pagado no puede ser negativo")
    BigDecimal precioPagado,
    @NotNull(message = "La fecha de vencimiento es obligatoria")
    @Future(message = "La fecha de vencimiento debe ser posterior a hoy")
    LocalDate fechaVencimiento,
    // Cómo se cobró la venta (efectivo o tarjeta). Obligatorio salvo que el bono sea sin cargo.
    MetodoPago metodo
) {}
