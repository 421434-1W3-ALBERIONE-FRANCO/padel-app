package com.padel.mapper;

import com.padel.domain.entity.SolicitudPartido;
import com.padel.dto.response.PostulacionSolicitudResponse;
import com.padel.dto.response.SolicitudPartidoResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SolicitudPartidoMapper {

    public SolicitudPartidoResponse toResponse(SolicitudPartido entity, List<PostulacionSolicitudResponse> postulaciones) {
        return new SolicitudPartidoResponse(
                entity.getId(),
                entity.getCreador().getId(),
                entity.getCreador().getNombre() + " " + entity.getCreador().getApellido(),
                entity.getCreador().getEmail(),
                entity.getTipoSolicitud(),
                entity.getCategoria(),
                entity.getCantidadJugadoresFaltantes(),
                entity.getFechaHoraPropuesta(),
                entity.getCancha() != null ? entity.getCancha().getId() : null,
                entity.getCancha() != null ? entity.getCancha().getNombre() : null,
                entity.getDescripcion(),
                entity.getEstado(),
                entity.getCreatedAt(),
                postulaciones
        );
    }
}
