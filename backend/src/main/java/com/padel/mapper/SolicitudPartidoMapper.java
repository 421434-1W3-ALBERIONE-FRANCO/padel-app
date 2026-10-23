package com.padel.mapper;

import com.padel.domain.entity.SolicitudPartido;
import com.padel.domain.entity.Usuario;
import com.padel.dto.response.ContactoResponse;
import com.padel.dto.response.PostulacionSolicitudResponse;
import com.padel.dto.response.SolicitudPartidoResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SolicitudPartidoMapper {

    public SolicitudPartidoResponse toResponse(SolicitudPartido entity, ContactoResponse creadorContacto,
                                               List<PostulacionSolicitudResponse> postulaciones, long cantidadPostulaciones) {
        return new SolicitudPartidoResponse(
                entity.getId(),
                entity.getCreador().getId(),
                nombrePublico(entity.getCreador()),
                creadorContacto,
                entity.getTipoSolicitud(),
                entity.getCategoria(),
                entity.getCantidadJugadoresFaltantes(),
                entity.getFechaHoraPropuesta(),
                entity.getCancha() != null ? entity.getCancha().getId() : null,
                entity.getCancha() != null ? entity.getCancha().getNombre() : null,
                entity.getDescripcion(),
                entity.getEstado(),
                entity.getCreatedAt(),
                postulaciones,
                cantidadPostulaciones
        );
    }

    /** "Juan P.": el apellido completo no hace falta para coordinar un partido en el tablón. */
    public static String nombrePublico(Usuario usuario) {
        String apellido = usuario.getApellido();
        return apellido == null || apellido.isBlank()
                ? usuario.getNombre()
                : usuario.getNombre() + " " + apellido.charAt(0) + ".";
    }
}
