package com.padel.controller;

import com.padel.dto.request.CanchaRequest;
import com.padel.dto.response.CanchaResponse;
import com.padel.service.CanchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/canchas")
@RequiredArgsConstructor
@Tag(name = "Canchas", description = "Endpoints para la gestión de canchas de pádel")
@SecurityRequirement(name = "Bearer Authentication")
public class CanchaController {

    private final CanchaService canchaService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear una nueva cancha", description = "Crea una cancha en el sistema. Sólo accesible por administradores.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Cancha creada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos o nombre duplicado"),
        @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<CanchaResponse> crear(@Valid @RequestBody CanchaRequest request) {
        CanchaResponse response = canchaService.crear(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Obtener todas las canchas activas", description = "Retorna una lista con todas las canchas registradas en estado activo.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de canchas obtenida exitosamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<CanchaResponse>> obtenerTodasActivas() {
        List<CanchaResponse> response = canchaService.obtenerTodasActivas();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener cancha activa por ID", description = "Retorna los detalles de una cancha activa específica.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cancha encontrada y retornada"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Cancha no encontrada o inactiva")
    })
    public ResponseEntity<CanchaResponse> obtenerActivaPorId(@PathVariable Long id) {
        CanchaResponse response = canchaService.obtenerActivaPorId(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar una cancha", description = "Modifica los datos de una cancha activa existente. Sólo accesible por administradores.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cancha actualizada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos o nombre de cancha ya utilizado por otra"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Cancha no encontrada o inactiva")
    })
    public ResponseEntity<CanchaResponse> actualizar(@PathVariable Long id, @Valid @RequestBody CanchaRequest request) {
        CanchaResponse response = canchaService.actualizar(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar cancha (Soft Delete)", description = "Marca la cancha como inactiva (desactivada) sin eliminarla físicamente. Sólo accesible por administradores.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Cancha desactivada exitosamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Cancha no encontrada o ya inactiva")
    })
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        canchaService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
