package com.padel.controller;

import com.padel.dto.response.NotificacionResponse;
import com.padel.service.NotificacionInAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
@Tag(name = "Notificaciones", description = "Notificaciones in-app del usuario autenticado")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("isAuthenticated()")
public class NotificacionController {

    private final NotificacionInAppService notificacionInAppService;

    @GetMapping
    @Operation(summary = "Listar mis notificaciones", description = "Retorna todas las notificaciones del usuario autenticado, más recientes primero.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente")
    })
    public ResponseEntity<List<NotificacionResponse>> listarMias(Principal principal) {
        return ResponseEntity.ok(notificacionInAppService.listarMias(principal.getName()));
    }

    @GetMapping("/no-leidas/contador")
    @Operation(summary = "Contar notificaciones no leídas", description = "Retorna la cantidad de notificaciones no leídas del usuario autenticado.")
    public ResponseEntity<Map<String, Long>> contarNoLeidas(Principal principal) {
        long count = notificacionInAppService.contarNoLeidas(principal.getName());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PatchMapping("/{id}/leida")
    @Operation(summary = "Marcar una notificación como leída")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Notificación marcada como leída"),
        @ApiResponse(responseCode = "404", description = "Notificación no encontrada para el usuario")
    })
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id, Principal principal) {
        notificacionInAppService.marcarLeida(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/leer-todas")
    @Operation(summary = "Marcar todas mis notificaciones como leídas")
    public ResponseEntity<Void> marcarTodasLeidas(Principal principal) {
        notificacionInAppService.marcarTodasLeidas(principal.getName());
        return ResponseEntity.noContent().build();
    }
}
