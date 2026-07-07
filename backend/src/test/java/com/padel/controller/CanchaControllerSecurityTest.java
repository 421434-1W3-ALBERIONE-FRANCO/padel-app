package com.padel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.CanchaRequest;
import com.padel.domain.enums.TipoSuelo;
import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.response.LoginResponse;
import com.padel.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que las restricciones @PreAuthorize agregadas a CanchaController
 * (crear cancha = solo ADMIN) se cumplen de punta a punta: un JUGADOR recibe 403
 * y un ADMIN puede operar con normalidad.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CanchaControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private String obtenerTokenParaRol(String email, RolUsuario rol) throws Exception {
        CrearUsuarioRequest registro = new CrearUsuarioRequest(
                "Test", "User", email, null, "password123", RolUsuario.JUGADOR);

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registro)));

        if (rol != RolUsuario.JUGADOR) {
            Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
            usuario.setRol(rol);
            usuarioRepository.save(usuario);
        }

        LoginRequest login = new LoginRequest(email, "password123");
        String responseJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(responseJson, LoginResponse.class).token();
    }

    @Test
    void jugadorNoPuedeCrearCancha() throws Exception {
        String token = obtenerTokenParaRol("jugador.security.test@test.com", RolUsuario.JUGADOR);
        CanchaRequest request = new CanchaRequest("Cancha Test", TipoSuelo.CEMENTO, false, false);

        mockMvc.perform(post("/api/v1/canchas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPuedeCrearCancha() throws Exception {
        String token = obtenerTokenParaRol("admin.security.test@test.com", RolUsuario.ADMIN);
        CanchaRequest request = new CanchaRequest("Cancha Test Admin", TipoSuelo.BLINDEX, true, true);

        mockMvc.perform(post("/api/v1/canchas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void registroPublicoIgnoraRolEnviadoPorElCliente() throws Exception {
        String email = "intento.admin@test.com";
        CrearUsuarioRequest registro = new CrearUsuarioRequest(
                "Hacker", "Test", email, null, "password123", RolUsuario.ADMIN);

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registro)))
                .andExpect(status().isCreated());

        Usuario creado = usuarioRepository.findByEmail(email).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(RolUsuario.JUGADOR, creado.getRol());
    }
}
