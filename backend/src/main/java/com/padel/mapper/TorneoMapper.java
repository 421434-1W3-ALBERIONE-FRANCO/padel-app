package com.padel.mapper;

import com.padel.domain.entity.Torneo;
import com.padel.dto.request.TorneoRequest;
import com.padel.dto.response.TorneoResponse;
import org.springframework.stereotype.Component;

@Component
public class TorneoMapper {

    public Torneo toEntity(TorneoRequest request) {
        return Torneo.builder()
                .nombre(request.nombre())
                .tipo(request.tipo())
                .formato(request.formato())
                .categoria(request.categoria())
                .fechaInicio(request.fechaInicio())
                .fechaFin(request.fechaFin())
                .maxParejas(request.maxParejas())
                .precioInscripcion(request.precioInscripcion())
                .descripcion(request.descripcion())
                .build();
    }

    public void updateFromRequest(TorneoRequest request, Torneo entity) {
        entity.setNombre(request.nombre());
        entity.setTipo(request.tipo());
        entity.setFormato(request.formato());
        entity.setCategoria(request.categoria());
        entity.setFechaInicio(request.fechaInicio());
        entity.setFechaFin(request.fechaFin());
        entity.setMaxParejas(request.maxParejas());
        entity.setPrecioInscripcion(request.precioInscripcion());
        entity.setDescripcion(request.descripcion());
    }

    public TorneoResponse toResponse(Torneo entity, long parejasInscriptas) {
        return new TorneoResponse(
                entity.getId(),
                entity.getNombre(),
                entity.getTipo(),
                entity.getFormato(),
                entity.getCategoria(),
                entity.getFechaInicio(),
                entity.getFechaFin(),
                entity.getMaxParejas(),
                parejasInscriptas,
                entity.getPrecioInscripcion(),
                entity.getDescripcion(),
                entity.getEstado()
        );
    }
}
