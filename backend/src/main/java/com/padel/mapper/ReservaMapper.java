package com.padel.mapper;

import com.padel.domain.entity.Reserva;
import com.padel.dto.response.ReservaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReservaMapper {

    @Mapping(target = "usuarioId", source = "usuario.id")
    @Mapping(target = "usuarioEmail", source = "usuario.email")
    @Mapping(target = "canchaId", source = "cancha.id")
    @Mapping(target = "canchaNombre", source = "cancha.nombre")
    @Mapping(target = "franjaId", source = "franjaHoraria.id")
    ReservaResponse toResponse(Reserva reserva);
}
