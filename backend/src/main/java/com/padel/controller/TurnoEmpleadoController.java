package com.padel.controller;

import com.padel.dto.request.TurnoRequest;
import com.padel.dto.response.TurnoResponse;
import com.padel.service.TurnoEmpleadoService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/turnos")
@RequiredArgsConstructor
@Tag(name = "Turnos de Empleado", description = "Gestión de turnos laborales del staff para el control horario (Admin)")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN')")
public class TurnoEmpleadoController {

    private final TurnoEmpleadoService turnoEmpleadoService;

    @PostMapping
    @Operation(summary = "Crear un turno para un empleado")
    public ResponseEntity<TurnoResponse> crear(@Valid @RequestBody TurnoRequest request) {
        return new ResponseEntity<>(turnoEmpleadoService.crear(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un turno")
    public ResponseEntity<TurnoResponse> actualizar(@PathVariable Long id, @Valid @RequestBody TurnoRequest request) {
        return ResponseEntity.ok(turnoEmpleadoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un turno")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        turnoEmpleadoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Listar los turnos de un empleado")
    public ResponseEntity<List<TurnoResponse>> listarPorEmpleado(@RequestParam Long empleadoId) {
        return ResponseEntity.ok(turnoEmpleadoService.listarPorEmpleado(empleadoId));
    }
}
