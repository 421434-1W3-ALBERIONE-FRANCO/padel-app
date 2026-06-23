package com.padel.mapper;

import com.padel.domain.entity.BloqueoCancha;
import com.padel.dto.request.BloqueoCanchaRequest;
import com.padel.dto.response.BloqueoCanchaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BloqueoCanchaMapper {

    @Mapping(target = "canchaId", source = "cancha.id")
    BloqueoCanchaResponse toResponse(BloqueoCancha bloqueo);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cancha", ignore = true)
    BloqueoCancha toEntity(BloqueoCanchaRequest request);
}
