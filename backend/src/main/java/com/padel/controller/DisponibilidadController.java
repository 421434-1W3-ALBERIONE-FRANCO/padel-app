package com.padel.controller;

import com.padel.dto.response.DisponibilidadResponse;
import com.padel.service.DisponibilidadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@Tag(name = "Disponibilidad", description = "Endpoints para la consulta de disponibilidad de canchas")
@SecurityRequirement(name = "Bearer Authentication")
public class DisponibilidadController {

    private final DisponibilidadService disponibilidadService;

    @GetMapping("/api/v1/disponibilidad")
    @Operation(summary = "Consultar disponibilidad de una cancha", description = "Retorna la grilla de slots de tiempo indicando cuáles están disponibles y sus precios calculados para una fecha específica.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Disponibilidad consultada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Parámetros de consulta inválidos"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Cancha no encontrada o inactiva")
    })
    public ResponseEntity<DisponibilidadResponse> obtenerDisponibilidad(
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam("cancha") Long canchaId) {
        DisponibilidadResponse response = disponibilidadService.obtenerDisponibilidad(canchaId, fecha);
        return ResponseEntity.ok(response);
    }
}
