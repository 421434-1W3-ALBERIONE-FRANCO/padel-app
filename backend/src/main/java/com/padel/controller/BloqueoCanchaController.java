package com.padel.controller;

import com.padel.dto.request.BloqueoCanchaRequest;
import com.padel.dto.response.BloqueoCanchaResponse;
import com.padel.service.BloqueoCanchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Bloqueos de Cancha", description = "Endpoints para la gestión de bloqueos temporales de las canchas")
@SecurityRequirement(name = "Bearer Authentication")
public class BloqueoCanchaController {

    private final BloqueoCanchaService bloqueoCanchaService;

    @PostMapping("/api/v1/canchas/{canchaId}/bloqueos")
    @Operation(summary = "Crear un nuevo bloqueo para una cancha", description = "Registra un bloqueo temporal para una cancha en un rango de fecha y hora. Valida que no se solape con otros bloqueos.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Bloqueo creado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Rango de fechas o de horas inválido, o solapamiento detectado"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Cancha no encontrada o inactiva")
    })
    public ResponseEntity<BloqueoCanchaResponse> crear(
            @PathVariable Long canchaId,
            @Valid @RequestBody BloqueoCanchaRequest request) {
        BloqueoCanchaResponse response = bloqueoCanchaService.crear(canchaId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/canchas/{canchaId}/bloqueos")
    @Operation(summary = "Obtener todos los bloqueos de una cancha", description = "Retorna la lista de todos los bloqueos temporales asociados a la cancha especificada.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de bloqueos obtenida exitosamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Cancha no encontrada")
    })
    public ResponseEntity<List<BloqueoCanchaResponse>> obtenerPorCanchaId(@PathVariable Long canchaId) {
        List<BloqueoCanchaResponse> response = bloqueoCanchaService.obtenerPorCanchaId(canchaId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/bloqueos/{id}")
    @Operation(summary = "Obtener bloqueo por ID", description = "Retorna los detalles de un bloqueo específico.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bloqueo encontrado y retornado"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Bloqueo no encontrado")
    })
    public ResponseEntity<BloqueoCanchaResponse> obtenerPorId(@PathVariable Long id) {
        BloqueoCanchaResponse response = bloqueoCanchaService.obtenerPorId(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/api/v1/bloqueos/{id}")
    @Operation(summary = "Actualizar un bloqueo", description = "Modifica los datos de un bloqueo existente. Revalida rangos y solapamientos.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bloqueo actualizado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Rango de fechas o de horas inválido, o solapamiento detectado"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Bloqueo no encontrado")
    })
    public ResponseEntity<BloqueoCanchaResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody BloqueoCanchaRequest request) {
        BloqueoCanchaResponse response = bloqueoCanchaService.actualizar(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api/v1/bloqueos/{id}")
    @Operation(summary = "Eliminar un bloqueo", description = "Elimina físicamente el bloqueo de la base de datos.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Bloqueo eliminado exitosamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Bloqueo no encontrado")
    })
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        bloqueoCanchaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
