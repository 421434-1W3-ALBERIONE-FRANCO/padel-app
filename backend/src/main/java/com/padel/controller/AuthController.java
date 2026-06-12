package com.padel.controller;

import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.response.LoginResponse;
import com.padel.dto.response.UsuarioResponse;
import com.padel.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
