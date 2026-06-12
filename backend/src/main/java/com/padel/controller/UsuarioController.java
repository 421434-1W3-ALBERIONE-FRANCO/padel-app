package com.padel.controller;

import com.padel.dto.response.UsuarioResponse;
import com.padel.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Endpoints para la gestión de usuarios y consulta de perfiles")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/me")
    @Operation(
        summary = "Obtener perfil del usuario autenticado", 
        description = "Retorna los detalles del perfil del usuario actual a partir del token JWT enviado.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Perfil retornado exitosamente"),
        @ApiResponse(responseCode = "401", description = "Token no provisto, inválido o expirado"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<UsuarioResponse> obtenerPerfilActual(Authentication authentication) {
        String email = authentication.getName();
        UsuarioResponse response = usuarioService.obtenerPerfilPorEmail(email);
        return ResponseEntity.ok(response);
    }
}
