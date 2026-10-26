package com.padel.mapper;

import com.padel.domain.entity.PostulacionSolicitud;
import com.padel.dto.response.ContactoResponse;
import com.padel.dto.response.PostulacionSolicitudResponse;
import org.springframework.stereotype.Component;

@Component
public class PostulacionSolicitudMapper {

    public PostulacionSolicitudResponse toResponse(PostulacionSolicitud entity, ContactoResponse contacto) {
        return new PostulacionSolicitudResponse(
                entity.getId(),
                entity.getSolicitud().getId(),
                entity.getJugador().getId(),
                SolicitudPartidoMapper.nombrePublico(entity.getJugador()),
                contacto,
                entity.getMensaje(),
                entity.getEstado(),
                entity.getCreatedAt()
        );
    }
}
