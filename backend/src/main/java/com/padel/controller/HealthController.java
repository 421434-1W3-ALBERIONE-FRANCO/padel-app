package com.padel.controller;

import com.padel.dto.response.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Monitoreo", description = "Endpoints para monitoreo de estado del sistema")
public class HealthController {

    @GetMapping
    @Operation(summary = "Obtener estado de salud del sistema", description = "Retorna el estado de disponibilidad actual del servidor.")
    public ResponseEntity<HealthResponse> checkHealth() {
        return ResponseEntity.ok(new HealthResponse("UP"));
    }
}
