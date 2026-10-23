package com.padel.dto.request;

import com.padel.domain.enums.MetodoPago;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Venta (o regalo) de un bono del catálogo. Las horas, el precio y el vencimiento salen del tipo de bono:
 * quien vende ya no los elige.
 */
public record CrearBonoRequest(
    @NotBlank(message = "El email de usuario es obligatorio")
    @Email(message = "El formato de email no es válido")
    @Size(max = 150, message = "El email no puede superar los 150 caracteres")
    String usuarioEmail,

    @NotNull(message = "El tipo de bono es obligatorio")
    Long tipoBonoId,

    // Cómo se cobró la venta (efectivo o tarjeta). Obligatorio salvo que el bono sea sin cargo.
    MetodoPago metodo,

    // Bono sin cargo (regalo, compensación): solo un ADMIN, y con motivo.
    boolean sinCargo,

    @Size(max = 255, message = "El motivo no puede superar los 255 caracteres")
    String motivoSinCargo
) {}
