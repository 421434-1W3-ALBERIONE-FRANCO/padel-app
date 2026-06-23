package com.padel.controller;

import com.padel.dto.request.FranjaHorariaRequest;
import com.padel.dto.response.FranjaHorariaResponse;
import com.padel.service.FranjaHorariaService;
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
@Tag(name = "Franjas Horarias", description = "Endpoints para la gestión de franjas horarias de las canchas")
@SecurityRequirement(name = "Bearer Authentication")
public class FranjaHorariaController {

    private final FranjaHorariaService franjaHorariaService;

    @PostMapping("/api/v1/canchas/{canchaId}/franjas")
    @Operation(summary = "Crear una nueva franja horaria para una cancha", description = "Asocia una franja horaria a una cancha activa. Valida que no se solape con franjas existentes.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Franja horaria creada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Rango de horario inválido, días vacíos o solapamiento detectado"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Cancha no encontrada o inactiva")
    })
    public ResponseEntity<FranjaHorariaResponse> crear(
            @PathVariable Long canchaId,
            @Valid @RequestBody FranjaHorariaRequest request) {
        FranjaHorariaResponse response = franjaHorariaService.crear(canchaId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/canchas/{canchaId}/franjas")
    @Operation(summary = "Obtener todas las franjas horarias de una cancha", description = "Retorna la lista de todas las franjas horarias asociadas a la cancha especificada.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de franjas horarias obtenida exitosamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Cancha no encontrada")
    })
    public ResponseEntity<List<FranjaHorariaResponse>> obtenerPorCanchaId(@PathVariable Long canchaId) {
        List<FranjaHorariaResponse> response = franjaHorariaService.obtenerPorCanchaId(canchaId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/franjas-horarias/{id}")
    @Operation(summary = "Obtener franja horaria por ID", description = "Retorna los detalles de una franja horaria específica.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Franja horaria encontrada y retornada"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Franja horaria no encontrada o cancha asociada inactiva")
    })
    public ResponseEntity<FranjaHorariaResponse> obtenerPorId(@PathVariable Long id) {
        FranjaHorariaResponse response = franjaHorariaService.obtenerPorId(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/api/v1/franjas-horarias/{id}")
    @Operation(summary = "Actualizar una franja horaria", description = "Modifica los datos de una franja horaria existente. Revalida rangos y solapamientos.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Franja horaria actualizada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Rango de horario inválido o solapamiento con otras franjas"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Franja horaria no encontrada")
    })
    public ResponseEntity<FranjaHorariaResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody FranjaHorariaRequest request) {
        FranjaHorariaResponse response = franjaHorariaService.actualizar(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api/v1/franjas-horarias/{id}")
    @Operation(summary = "Eliminar una franja horaria", description = "Elimina físicamente la franja horaria de la base de datos.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Franja horaria eliminada exitosamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Franja horaria no encontrada")
    })
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        franjaHorariaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
