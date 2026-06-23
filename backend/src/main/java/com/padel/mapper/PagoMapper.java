package com.padel.mapper;

import com.padel.domain.entity.Pago;
import com.padel.dto.response.PagoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PagoMapper {
    @Mapping(target = "reservaId", source = "reserva.id")
    @Mapping(target = "usuarioId", source = "usuario.id")
    @Mapping(target = "usuarioEmail", source = "usuario.email")
    PagoResponse toResponse(Pago pago);
}
