package com.padel.controller;

import com.padel.dto.request.CerrarCuentaRequest;
import com.padel.dto.request.ConsumoRequest;
import com.padel.dto.response.ConsumoResponse;
import com.padel.service.ConsumoService;
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
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
@Tag(name = "Consumos", description = "Endpoints para la gestión de consumos y cobranzas en el turno")
@SecurityRequirement(name = "Bearer Authentication")
public class ConsumoController {

    private final ConsumoService consumoService;

    @PostMapping("/{id}/consumos")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    @Operation(summary = "Cargar un consumo a una reserva", description = "Registra un consumo de producto en la reserva especificada. Descuenta stock al instante. Accesible por administradores y recepcionistas.")
    public ResponseEntity<ConsumoResponse> cargarConsumo(
            @PathVariable Long id,
            @Valid @RequestBody ConsumoRequest request) {
        ConsumoResponse response = consumoService.cargarConsumo(id, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}/consumos")
    @Operation(summary = "Ver consumos de una reserva", description = "Retorna el listado de consumos asociados a la reserva. Accesible por el dueño de la reserva, administradores y recepcionistas.")
    public ResponseEntity<List<ConsumoResponse>> obtenerConsumos(
            @PathVariable Long id,
            Principal principal) {
        List<ConsumoResponse> response = consumoService.obtenerConsumosPorReserva(id, principal.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/cerrar-cuenta")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    @Operation(summary = "Cerrar la cuenta de consumos", description = "Registra el cobro de consumos pendientes y los marca como pagados. Genera el registro de pago respectivo. Accesible por administradores y recepcionistas.")
    public ResponseEntity<Void> cerrarCuenta(
            @PathVariable Long id,
            @Valid @RequestBody CerrarCuentaRequest request) {
        consumoService.cerrarCuenta(id, request);
        return ResponseEntity.ok().build();
    }
}
