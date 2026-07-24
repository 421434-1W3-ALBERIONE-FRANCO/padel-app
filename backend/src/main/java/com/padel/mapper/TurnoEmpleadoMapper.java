package com.padel.mapper;

import com.padel.domain.entity.TurnoEmpleado;
import com.padel.dto.response.TurnoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TurnoEmpleadoMapper {
    @Mapping(target = "empleadoId", source = "empleado.id")
    @Mapping(target = "empleadoEmail", source = "empleado.email")
    TurnoResponse toResponse(TurnoEmpleado turno);
}
