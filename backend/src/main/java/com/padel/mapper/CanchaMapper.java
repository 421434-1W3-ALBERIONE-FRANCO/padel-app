package com.padel.mapper;

import com.padel.domain.entity.Cancha;
import com.padel.dto.request.CanchaRequest;
import com.padel.dto.response.CanchaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CanchaMapper {

    CanchaResponse toResponse(Cancha cancha);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "activa", ignore = true)
    Cancha toEntity(CanchaRequest request);
}
