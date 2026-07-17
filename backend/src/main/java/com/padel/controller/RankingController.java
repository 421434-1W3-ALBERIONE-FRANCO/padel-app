package com.padel.controller;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.dto.response.RankingJugadorResponse;
import com.padel.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
@Tag(name = "Rankings", description = "Endpoints para consultar el ranking de jugadores por categoría")
@SecurityRequirement(name = "Bearer Authentication")
public class RankingController {

    private final RankingService rankingService;

    @GetMapping("/{categoria}")
    @Operation(summary = "Obtener ranking por categoría", description = "Retorna el ranking de jugadores de una categoría, ordenado por puntos.")
    public ResponseEntity<List<RankingJugadorResponse>> obtenerRankingPorCategoria(@PathVariable CategoriaJugador categoria) {
        return ResponseEntity.ok(rankingService.obtenerRankingPorCategoria(categoria));
    }

    @GetMapping("/{categoria}/mi-posicion")
    @Operation(summary = "Obtener mi posición en el ranking", description = "Retorna la posición y puntaje del usuario autenticado en la categoría indicada.")
    public ResponseEntity<RankingJugadorResponse> obtenerMiPosicion(@PathVariable CategoriaJugador categoria, Principal principal) {
        return ResponseEntity.ok(rankingService.obtenerMiPosicion(categoria, principal.getName()));
    }
}
