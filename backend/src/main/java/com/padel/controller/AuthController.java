package com.padel.controller;

import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.request.ResetearPasswordRequest;
import com.padel.dto.request.SolicitarRecuperacionRequest;
import com.padel.dto.response.LoginResponse;
import com.padel.dto.response.UsuarioResponse;
import com.padel.service.UsuarioService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Endpoints para registro y login de usuarios")
public class AuthController {

    private final UsuarioService usuarioService;

    @PostMapping("/registro")
    @Operation(summary = "Registrar un nuevo usuario", description = "Crea un usuario en el sistema. Por defecto tiene el rol de JUGADOR.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Usuario registrado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos o email duplicado")
    })
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody CrearUsuarioRequest request) {
        UsuarioResponse response = usuarioService.registrar(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Valida las credenciales del usuario y genera un token JWT.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Sesión iniciada exitosamente, retorna JWT"),
        @ApiResponse(responseCode = "401", description = "Credenciales incorrectas")
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = usuarioService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/recuperar-password")
    @Operation(
        summary = "Solicitar recuperación de contraseña",
        description = "Si el email existe, envía un enlace de recuperación válido por 1 hora. Responde 200 siempre, exista o no el email, para no revelar qué cuentas están registradas."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Solicitud procesada")
    })
    public ResponseEntity<Void> solicitarRecuperacion(@Valid @RequestBody SolicitarRecuperacionRequest request) {
        usuarioService.solicitarRecuperacionPassword(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resetear-password")
    @Operation(summary = "Restablecer contraseña con token", description = "Usa el token recibido por email para establecer una nueva contraseña.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Contraseña restablecida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Token inválido, expirado o ya utilizado")
    })
    public ResponseEntity<Void> resetearPassword(@Valid @RequestBody ResetearPasswordRequest request) {
        usuarioService.resetearPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Cerrar sesión", description = "Cierra la sesión de trabajo del staff (control horario). El JWT es stateless: el token no se invalida en el servidor, el frontend lo descarta.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Sesión cerrada"),
        @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<Void> logout(Principal principal) {
        usuarioService.logout(principal.getName());
        return ResponseEntity.noContent().build();
    }
}
