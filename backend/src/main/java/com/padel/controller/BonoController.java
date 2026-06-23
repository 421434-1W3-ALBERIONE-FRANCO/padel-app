package com.padel.controller;

import com.padel.dto.request.CrearBonoRequest;
import com.padel.dto.response.BonoResponse;
import com.padel.dto.response.PagoResponse;
import com.padel.service.BonoService;
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

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Bonos", description = "Endpoints para la gestión, compra y uso de bonos prepago de horas")
@SecurityRequirement(name = "Bearer Authentication")
public class BonoController {

    private final BonoService bonoService;

    @PostMapping("/bonos")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Asignar un bono a un usuario (Admin)", description = "Permite a un administrador crear y vender un bono de horas a un usuario.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Bono asignado correctamente"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Permisos insuficientes"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<BonoResponse> asignarBono(@Valid @RequestBody CrearBonoRequest request) {
        BonoResponse response = bonoService.asignarBono(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/bonos/mis-bonos")
    @Operation(summary = "Obtener bonos del usuario logueado", description = "Retorna el listado completo de bonos del usuario autenticado actual.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Listado obtenido correctamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<BonoResponse>> obtenerMisBonos(Principal principal) {
        List<BonoResponse> response = bonoService.obtenerMisBonos(principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bonos/{id}")
    @Operation(summary = "Obtener bono por ID", description = "Retorna los detalles de un bono específico. Sólo accesible por el dueño o un administrador/recepcionista.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bono encontrado"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Bono o usuario no encontrado")
    })
    public ResponseEntity<BonoResponse> obtenerPorId(
            @PathVariable Long id,
            Principal principal) {
        BonoResponse response = bonoService.obtenerPorId(id, principal.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reservas/{id}/usar-bono")
    @Operation(summary = "Pagar una reserva con saldo de bono", description = "Aplica el descuento de horas de un bono activo del usuario para saldar y confirmar una reserva pre-existente.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Reserva confirmada y pagada con bono"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "409", description = "Bono no disponible o saldo insuficiente"),
        @ApiResponse(responseCode = "404", description = "Reserva o usuario no encontrado")
    })
    public ResponseEntity<PagoResponse> usarBono(
            @PathVariable Long id,
            Principal principal) {
        PagoResponse response = bonoService.usarBono(id, principal.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
