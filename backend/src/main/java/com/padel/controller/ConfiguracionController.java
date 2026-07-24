package com.padel.controller;

import com.padel.dto.request.ConfiguracionRequest;
import com.padel.dto.response.ConfiguracionResponse;
import com.padel.service.ConfiguracionSistemaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/configuracion")
@RequiredArgsConstructor
@Tag(name = "Configuración", description = "Configuración global del sistema (feature flags administrables por el admin)")
@SecurityRequirement(name = "Bearer Authentication")
public class ConfiguracionController {

    private final ConfiguracionSistemaService configuracionSistemaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    @Operation(summary = "Obtener configuración del sistema", description = "Retorna los parámetros globales (comisiones, avisos de bono, control horario).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuración obtenida exitosamente")
    })
    public ResponseEntity<ConfiguracionResponse> obtener() {
        return ResponseEntity.ok(configuracionSistemaService.obtener());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar configuración del sistema (Admin)", description = "Permite al administrador activar/desactivar comisiones y ajustar los parámetros globales.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuración actualizada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
        @ApiResponse(responseCode = "403", description = "Permisos insuficientes")
    })
    public ResponseEntity<ConfiguracionResponse> actualizar(@Valid @RequestBody ConfiguracionRequest request) {
        return ResponseEntity.ok(configuracionSistemaService.actualizar(request));
    }
}
