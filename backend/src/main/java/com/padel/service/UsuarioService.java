package com.padel.service;

import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.response.LoginResponse;
import com.padel.dto.response.UsuarioResponse;

public interface UsuarioService {
    UsuarioResponse registrar(CrearUsuarioRequest request);
    LoginResponse login(LoginRequest request);
    UsuarioResponse obtenerPerfilPorEmail(String email);
}
