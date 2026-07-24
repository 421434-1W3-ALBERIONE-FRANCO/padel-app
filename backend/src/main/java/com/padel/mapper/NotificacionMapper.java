package com.padel.mapper;

import com.padel.domain.entity.Notificacion;
import com.padel.dto.response.NotificacionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificacionMapper {
    NotificacionResponse toResponse(Notificacion notificacion);
}
