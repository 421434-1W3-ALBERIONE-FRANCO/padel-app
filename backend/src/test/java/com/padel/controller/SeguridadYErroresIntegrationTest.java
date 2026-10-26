package com.padel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.response.LoginResponse;
import com.padel.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de seguridad y errores que consume el frontend: 401 (no 403) sin sesión válida para
 * que el interceptor cierre la sesión, códigos correctos en vez de 500 para errores de framework y
 * handshake del WebSocket de disponibilidad accesible sin token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadYErroresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private void registrar(String email) throws Exception {
        CrearUsuarioRequest registro = new CrearUsuarioRequest(
                "Test", "User", email, null, "ClaveSegura-2026");
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registro)))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "ClaveSegura-2026"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, LoginResponse.class).token();
    }

    @Test
    void sinTokenResponde401ConApiError() throws Exception {
        mockMvc.perform(get("/api/v1/reservas/mis-reservas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/reservas/mis-reservas"));
    }

    @Test
    void tokenInvalidoResponde401() throws Exception {
        mockMvc.perform(get("/api/v1/reservas/mis-reservas")
                        .header("Authorization", "Bearer token.invalido.firmado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rutaInexistenteResponde404YNo500() throws Exception {
        String email = "rutas.inexistentes@test.com";
        registrar(email);
        String token = login(email);

        mockMvc.perform(get("/api/v1/no-existe").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void parametroConFormatoInvalidoResponde400() throws Exception {
        String email = "parametro.invalido@test.com";
        registrar(email);
        String token = login(email);

        mockMvc.perform(get("/api/v1/rankings/NOVENA").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginDeCuentaDesactivadaResponde401YNo500() throws Exception {
        String email = "cuenta.desactivada@test.com";
        registrar(email);
        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        usuario.setActivo(false);
        usuarioRepository.save(usuario);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "ClaveSegura-2026"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeUsuarioDesactivadoDejaDeSerValido() throws Exception {
        String email = "baja.con.token@test.com";
        registrar(email);
        String token = login(email);
        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        usuario.setActivo(false);
        usuarioRepository.save(usuario);

        mockMvc.perform(get("/api/v1/reservas/mis-reservas").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handshakeDelWebSocketNoExigeToken() throws Exception {
        int status = mockMvc.perform(get("/ws")).andReturn().getResponse().getStatus();
        assertNotEquals(401, status);
        assertNotEquals(403, status);
    }
}
