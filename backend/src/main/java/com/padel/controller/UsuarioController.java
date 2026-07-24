package com.padel.controller;

import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.CambiarPasswordRequest;
import com.padel.dto.request.CrearStaffRequest;
import com.padel.dto.response.UsuarioResponse;
import com.padel.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Cambiar la contraseña del usuario autenticado",
        description = "Requiere la contraseña actual para validar el cambio. Envía un email de confirmación.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Contraseña actualizada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Contraseña actual incorrecta o datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Token no provisto, inválido o expirado")
    })
    public ResponseEntity<Void> cambiarPassword(Authentication authentication, @Valid @RequestBody CambiarPasswordRequest request) {
        usuarioService.cambiarPassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Listar usuarios por rol (Admin)",
        description = "Retorna los usuarios que tienen el rol especificado. Pensado para poblar selectores administrativos (ej. elegir un recepcionista).",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<List<UsuarioResponse>> listarPorRol(@RequestParam RolUsuario rol) {
        return ResponseEntity.ok(usuarioService.listarPorRol(rol));
    }

    @PostMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Crear un perfil de staff (Admin)",
        description = "Da de alta una cuenta de personal (ADMIN o RECEPCIONISTA) con la que el empleado podrá iniciar sesión. No permite crear JUGADOR.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<UsuarioResponse> crearStaff(@Valid @RequestBody CrearStaffRequest request) {
        return new ResponseEntity<>(usuarioService.crearStaff(request), HttpStatus.CREATED);
    }
}
