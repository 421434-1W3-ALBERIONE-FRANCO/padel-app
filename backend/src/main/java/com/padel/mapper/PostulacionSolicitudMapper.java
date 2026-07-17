package com.padel.mapper;

import com.padel.domain.entity.PostulacionSolicitud;
import com.padel.dto.response.PostulacionSolicitudResponse;
import org.springframework.stereotype.Component;

@Component
public class PostulacionSolicitudMapper {

    public PostulacionSolicitudResponse toResponse(PostulacionSolicitud entity) {
        return new PostulacionSolicitudResponse(
                entity.getId(),
                entity.getSolicitud().getId(),
                entity.getJugador().getId(),
                entity.getJugador().getNombre() + " " + entity.getJugador().getApellido(),
                entity.getJugador().getEmail(),
                entity.getMensaje(),
                entity.getEstado(),
                entity.getCreatedAt()
        );
    }
}
