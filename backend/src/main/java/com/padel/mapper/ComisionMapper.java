package com.padel.mapper;

import com.padel.domain.entity.Comision;
import com.padel.dto.response.ComisionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ComisionMapper {
    ComisionResponse toResponse(Comision comision);
}
