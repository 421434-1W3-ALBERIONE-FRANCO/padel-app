package com.padel.mapper;

import com.padel.domain.entity.LiquidacionSueldo;
import com.padel.dto.response.LiquidacionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LiquidacionSueldoMapper {
    @Mapping(target = "empleadoId", source = "empleado.id")
    @Mapping(target = "empleadoEmail", source = "empleado.email")
    LiquidacionResponse toResponse(LiquidacionSueldo liquidacion);
}
