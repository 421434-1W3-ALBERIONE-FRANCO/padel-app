package com.padel.mapper;

import com.padel.domain.entity.InscripcionTorneo;
import com.padel.domain.entity.PartidoTorneo;
import com.padel.dto.response.PartidoTorneoResponse;
import org.springframework.stereotype.Component;

@Component
public class PartidoTorneoMapper {

    public PartidoTorneoResponse toResponse(PartidoTorneo entity) {
        return new PartidoTorneoResponse(
                entity.getId(),
                entity.getTorneo().getId(),
                entity.getRonda(),
                entity.getNumeroRonda(),
                entity.getInscripcion1() != null ? entity.getInscripcion1().getId() : null,
                nombresPareja(entity.getInscripcion1()),
                entity.getInscripcion2() != null ? entity.getInscripcion2().getId() : null,
                nombresPareja(entity.getInscripcion2()),
                entity.getFechaHora(),
                entity.getSetsPareja1(),
                entity.getSetsPareja2(),
                entity.getGanador() != null ? entity.getGanador().getId() : null,
                entity.getEstado()
        );
    }

    private String nombresPareja(InscripcionTorneo inscripcion) {
        if (inscripcion == null) {
            return null;
        }
        return inscripcion.getJugador1().getNombre() + " / " + inscripcion.getJugador2().getNombre();
    }
}
