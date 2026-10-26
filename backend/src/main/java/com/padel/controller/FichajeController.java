package com.padel.controller;

import com.padel.dto.response.FichajeResponse;
import com.padel.service.SesionTrabajoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/fichajes")
@RequiredArgsConstructor
@Tag(name = "Fichaje", description = "Entrada y salida de la jornada del staff (control horario)")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
public class FichajeController {

    private final SesionTrabajoService sesionTrabajoService;

    @GetMapping("/actual")
    @Operation(summary = "Estado del fichaje", description = "Si el empleado tiene la jornada abierta y desde cuándo.")
    public ResponseEntity<FichajeResponse> actual(Principal principal) {
        return ResponseEntity.ok(sesionTrabajoService.fichajeActual(principal.getName()));
    }

    @PostMapping
    @Operation(summary = "Fichar entrada", description = "Abre la jornada. Si el club configuró redes permitidas, solo desde ellas.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Jornada abierta"),
        @ApiResponse(responseCode = "403", description = "Red no permitida para fichar"),
        @ApiResponse(responseCode = "409", description = "Ya hay una jornada abierta")
    })
    public ResponseEntity<FichajeResponse> ficharEntrada(Principal principal, HttpServletRequest request) {
        return new ResponseEntity<>(sesionTrabajoService.ficharEntrada(principal.getName(), request.getRemoteAddr()), HttpStatus.CREATED);
    }

    @PutMapping("/actual")
    @Operation(summary = "Fichar salida", description = "Cierra la jornada abierta y acredita las horas extra al banco de horas.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Jornada cerrada"),
        @ApiResponse(responseCode = "403", description = "Red no permitida para fichar"),
        @ApiResponse(responseCode = "409", description = "No hay una jornada abierta")
    })
    public ResponseEntity<FichajeResponse> ficharSalida(Principal principal, HttpServletRequest request) {
        return ResponseEntity.ok(sesionTrabajoService.ficharSalida(principal.getName(), request.getRemoteAddr()));
    }
}
