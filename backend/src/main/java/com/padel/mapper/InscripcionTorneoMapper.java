package com.padel.mapper;

import com.padel.domain.entity.InscripcionTorneo;
import com.padel.dto.response.InscripcionTorneoResponse;
import org.springframework.stereotype.Component;

@Component
public class InscripcionTorneoMapper {

    public InscripcionTorneoResponse toResponse(InscripcionTorneo entity) {
        return new InscripcionTorneoResponse(
                entity.getId(),
                entity.getTorneo().getId(),
                entity.getJugador1().getId(),
                entity.getJugador1().getNombre() + " " + entity.getJugador1().getApellido(),
                entity.getJugador1().getEmail(),
                entity.getJugador2().getId(),
                entity.getJugador2().getNombre() + " " + entity.getJugador2().getApellido(),
                entity.getJugador2().getEmail(),
                entity.getEstado()
        );
    }
}
