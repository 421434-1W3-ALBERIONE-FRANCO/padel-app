package com.padel.mapper;

import com.padel.domain.entity.Consumo;
import com.padel.dto.response.ConsumoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ConsumoMapper {
    @Mapping(target = "reservaId", source = "reserva.id")
    @Mapping(target = "usuarioId", source = "usuario.id")
    @Mapping(target = "usuarioEmail", source = "usuario.email")
    @Mapping(target = "productoId", source = "producto.id")
    @Mapping(target = "productoNombre", source = "producto.nombre")
    @Mapping(target = "pagoId", source = "pago.id")
    ConsumoResponse toResponse(Consumo entity);
}
