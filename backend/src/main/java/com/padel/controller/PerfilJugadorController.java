package com.padel.controller;

import com.padel.dto.request.PerfilJugadorRequest;
import com.padel.dto.response.PerfilJugadorResponse;
import com.padel.service.PerfilJugadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/jugadores/me/perfil")
@RequiredArgsConstructor
@Tag(name = "Perfil de jugador", description = "Perfil deportivo del usuario autenticado: categoría, posición y disponibilidad semanal")
@SecurityRequirement(name = "Bearer Authentication")
public class PerfilJugadorController {

    private final PerfilJugadorService perfilJugadorService;

    @GetMapping
    @Operation(summary = "Obtener mi perfil de jugador", description = "Retorna el perfil deportivo del usuario autenticado.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Perfil encontrado"),
        @ApiResponse(responseCode = "404", description = "El usuario todavía no completó su perfil")
    })
    public ResponseEntity<PerfilJugadorResponse> obtener(Principal principal) {
        return ResponseEntity.ok(perfilJugadorService.obtenerMiPerfil(principal.getName()));
    }

    @PutMapping
    @Operation(summary = "Crear o actualizar mi perfil de jugador", description = "Guarda categoría, posición, presentación, visibilidad en el match y los bloques de la semana en los que puede jugar.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Perfil guardado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos")
    })
    public ResponseEntity<PerfilJugadorResponse> guardar(@Valid @RequestBody PerfilJugadorRequest request, Principal principal) {
        return ResponseEntity.ok(perfilJugadorService.guardarMiPerfil(request, principal.getName()));
    }
}
