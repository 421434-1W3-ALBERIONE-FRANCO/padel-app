package com.padel.mapper;

import com.padel.domain.entity.Bono;
import com.padel.dto.response.BonoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BonoMapper {
    @Mapping(target = "usuarioId", source = "usuario.id")
    @Mapping(target = "usuarioEmail", source = "usuario.email")
    BonoResponse toResponse(Bono bono);
}
