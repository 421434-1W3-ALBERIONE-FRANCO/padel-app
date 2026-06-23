package com.padel.mapper;

import com.padel.domain.entity.FranjaHoraria;
import com.padel.dto.request.FranjaHorariaRequest;
import com.padel.dto.response.FranjaHorariaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface FranjaHorariaMapper {

    @Mapping(target = "canchaId", source = "cancha.id")
    @Mapping(target = "diasAplicables", source = "diasAplicables", qualifiedByName = "stringToDays")
    FranjaHorariaResponse toResponse(FranjaHoraria franja);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cancha", ignore = true)
    @Mapping(target = "duracionMin", ignore = true)
    @Mapping(target = "diasAplicables", source = "diasAplicables", qualifiedByName = "daysToString")
    FranjaHoraria toEntity(FranjaHorariaRequest request);

    @Named("daysToString")
    default String daysToString(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            return "";
        }
        return days.stream()
                .map(DayOfWeek::name)
                .collect(Collectors.joining(","));
    }

    @Named("stringToDays")
    default Set<DayOfWeek> stringToDays(String daysString) {
        if (daysString == null || daysString.trim().isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(daysString.split(","))
                .map(String::trim)
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
    }
}
