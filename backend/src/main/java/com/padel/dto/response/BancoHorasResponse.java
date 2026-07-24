package com.padel.dto.response;

public record BancoHorasResponse(
    Long empleadoId,
    String empleadoEmail,
    Integer saldoMinutos
) {}
