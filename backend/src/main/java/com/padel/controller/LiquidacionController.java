package com.padel.controller;

import com.padel.dto.request.LiquidacionRequest;
import com.padel.dto.response.BancoHorasResponse;
import com.padel.dto.response.LiquidacionResponse;
import com.padel.service.LiquidacionService;
import com.padel.service.SesionTrabajoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/liquidaciones")
@RequiredArgsConstructor
@Tag(name = "Liquidaciones", description = "Liquidación de sueldos con horas trabajadas, banco de horas y comisiones (Admin)")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN')")
public class LiquidacionController {

    private final LiquidacionService liquidacionService;
    private final SesionTrabajoService sesionTrabajoService;

    @PostMapping
    @Operation(summary = "Generar una liquidación de sueldo", description = "Consolida minutos trabajados, banco de horas y comisiones pendientes de un empleado en un período. Marca los ítems como liquidados/pagados.")
    public ResponseEntity<LiquidacionResponse> liquidar(@Valid @RequestBody LiquidacionRequest request, Principal principal) {
        return new ResponseEntity<>(liquidacionService.liquidar(request, principal.getName()), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Historial de liquidaciones de un empleado")
    public ResponseEntity<List<LiquidacionResponse>> historial(@RequestParam Long empleadoId) {
        return ResponseEntity.ok(liquidacionService.historialPorEmpleado(empleadoId));
    }

    @GetMapping("/banco-horas")
    @Operation(summary = "Consultar el saldo de banco de horas de un empleado")
    public ResponseEntity<BancoHorasResponse> bancoHoras(@RequestParam Long empleadoId) {
        return ResponseEntity.ok(sesionTrabajoService.consultarBancoHoras(empleadoId));
    }
}
