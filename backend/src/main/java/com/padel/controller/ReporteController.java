package com.padel.controller;

import com.padel.dto.response.CajaDiariaResponse;
import com.padel.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Endpoints para reportes y auditoría del club")
@SecurityRequirement(name = "Bearer Authentication")
public class ReporteController {

    private final ReporteService reporteService;

    @GetMapping("/caja-diaria")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    @Operation(summary = "Obtener el reporte de caja diaria", description = "Retorna el total recaudado por método de pago para una fecha dada. Accesible por administradores y recepcionistas.")
    public ResponseEntity<CajaDiariaResponse> obtenerCajaDiaria(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        CajaDiariaResponse response = reporteService.obtenerCajaDiaria(fecha);
        return ResponseEntity.ok(response);
    }
}
