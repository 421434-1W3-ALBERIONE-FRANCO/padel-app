package com.padel.controller;

import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.request.ReenviarVerificacionRequest;
import com.padel.dto.request.ResetearPasswordRequest;
import com.padel.dto.request.SolicitarRecuperacionRequest;
import com.padel.dto.request.VerificarEmailRequest;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Endpoints para registro y login de usuarios")
public class AuthController {

    private final UsuarioService usuarioService;

    @PostMapping("/registro")
    @Operation(summary = "Registrar un nuevo usuario", description = "Crea una cuenta de JUGADOR. Si la verificación de email está activa, la cuenta queda pendiente hasta confirmar el enlace enviado por email.")
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
    @Operation(summary = "Cerrar sesión", description = "Revoca el token con el que se hace el pedido: deja de servir aunque todavía no haya vencido.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Sesión cerrada"),
        @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        usuarioService.logout(authorization.replaceFirst("^Bearer ", ""));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verificacion-email")
    @Operation(summary = "Confirmar el email", description = "Activa la cuenta con el token recibido por email al registrarse.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Email confirmado"),
        @ApiResponse(responseCode = "400", description = "Token inválido, vencido o ya usado"),
        @ApiResponse(responseCode = "429", description = "Demasiados intentos")
    })
    public ResponseEntity<Void> verificarEmail(@Valid @RequestBody VerificarEmailRequest request) {
        usuarioService.verificarEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verificacion-email/reenvio")
    @Operation(summary = "Reenviar el email de confirmación", description = "Responde 200 siempre, exista o no la cuenta, para no revelar qué emails están registrados.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Solicitud procesada"),
        @ApiResponse(responseCode = "429", description = "Demasiados intentos")
    })
    public ResponseEntity<Void> reenviarVerificacion(@Valid @RequestBody ReenviarVerificacionRequest request) {
        usuarioService.reenviarVerificacion(request.email());
        return ResponseEntity.ok().build();
    }
}
