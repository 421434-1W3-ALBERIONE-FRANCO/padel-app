package com.padel.mapper;

import com.padel.domain.entity.TipoBono;
import com.padel.dto.response.TipoBonoResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TipoBonoMapper {

    TipoBonoResponse toResponse(TipoBono tipoBono);
}
