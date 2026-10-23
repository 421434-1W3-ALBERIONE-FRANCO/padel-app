package com.padel.controller;

import com.padel.dto.response.PagoResponse;
import com.padel.service.ReintegroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reintegros")
@RequiredArgsConstructor
@Tag(name = "Reintegros", description = "Devoluciones de turnos cancelados que el staff tiene que entregar")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
public class ReintegroController {

    private final ReintegroService reintegroService;

    @GetMapping("/pendientes")
    @Operation(summary = "Reintegros pendientes", description = "Devoluciones por entregar en el mostrador y las de MercadoPago que no se pudieron procesar solas. Montos negativos.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reintegros pendientes"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Requiere rol ADMIN o RECEPCIONISTA")
    })
    public ResponseEntity<List<PagoResponse>> pendientes() {
        return ResponseEntity.ok(reintegroService.listarPendientes());
    }

    @PutMapping("/{id}/entrega")
    @Operation(summary = "Registrar la entrega de un reintegro", description = "Marca el reintegro como entregado: queda en la caja del día como movimiento negativo.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reintegro registrado"),
        @ApiResponse(responseCode = "404", description = "Reintegro no encontrado"),
        @ApiResponse(responseCode = "409", description = "El reintegro ya estaba entregado")
    })
    public ResponseEntity<PagoResponse> registrarEntrega(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(reintegroService.registrarEntrega(id, principal.getName()));
    }
}
