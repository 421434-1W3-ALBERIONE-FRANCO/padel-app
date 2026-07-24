package com.padel.controller;

import com.padel.dto.response.ReporteComisionesResponse;
import com.padel.service.ComisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/comisiones")
@RequiredArgsConstructor
@Tag(name = "Comisiones", description = "Reporte de comisiones ganadas por el staff de recepción")
@SecurityRequirement(name = "Bearer Authentication")
public class ComisionController {

    private final ComisionService comisionService;

    @GetMapping("/reporte")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reporte de comisiones de un recepcionista", description = "Retorna el detalle y total de comisiones generadas por un recepcionista en un rango de fechas.")
    public ResponseEntity<ReporteComisionesResponse> reporte(
            @RequestParam Long recepcionistaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(comisionService.reporte(recepcionistaId, desde, hasta));
    }
}
