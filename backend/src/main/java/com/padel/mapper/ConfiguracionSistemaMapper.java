package com.padel.mapper;

import com.padel.domain.entity.ConfiguracionSistema;
import com.padel.dto.response.ConfiguracionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConfiguracionSistemaMapper {
    ConfiguracionResponse toResponse(ConfiguracionSistema configuracion);
}
