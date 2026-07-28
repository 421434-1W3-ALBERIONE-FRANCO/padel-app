package com.padel.mapper;

import com.padel.domain.entity.Cancha;
import com.padel.dto.request.CanchaRequest;
import com.padel.dto.response.CanchaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface CanchaMapper {

    CanchaResponse toResponse(Cancha cancha);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "activa", ignore = true)
    Cancha toEntity(CanchaRequest request);

    // Conversión de duraciones: la entidad las guarda como "60,90,120,180";
    // los DTOs las exponen como lista de enteros ordenada.
    default List<Integer> stringToDuraciones(String s) {
        if (s == null || s.isBlank()) {
            return List.of(90);
        }
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .map(Integer::valueOf)
                .sorted()
                .toList();
    }

    default String duracionesToString(List<Integer> l) {
        if (l == null || l.isEmpty()) {
            return "90";
        }
        return l.stream()
                .filter(d -> d != null && d > 0)
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
