package com.padel.service;

import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.CambiarPasswordRequest;
import com.padel.dto.request.CrearStaffRequest;
import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.request.ResetearPasswordRequest;
import com.padel.dto.response.LoginResponse;
import com.padel.dto.response.UsuarioResponse;

import java.util.List;

public interface UsuarioService {
    UsuarioResponse registrar(CrearUsuarioRequest request);
    LoginResponse login(LoginRequest request);
    UsuarioResponse obtenerPerfilPorEmail(String email);
    void cambiarPassword(String email, CambiarPasswordRequest request);
    void solicitarRecuperacionPassword(String email);
    void resetearPassword(ResetearPasswordRequest request);
    List<UsuarioResponse> listarPorRol(RolUsuario rol);
    /** Revoca el token con el que se hizo el pedido. */
    void logout(String token);

    void verificarEmail(String token);

    /** Responde siempre igual (exista o no la cuenta) para no revelar qué emails están registrados. */
    void reenviarVerificacion(String email);
    UsuarioResponse crearStaff(CrearStaffRequest request);
}
