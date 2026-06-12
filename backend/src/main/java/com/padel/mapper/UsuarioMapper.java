package com.padel.mapper;

import com.padel.domain.entity.Usuario;
import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.response.UsuarioResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    UsuarioResponse toResponse(Usuario usuario);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "activo", ignore = true)
    Usuario toEntity(CrearUsuarioRequest request);
}
