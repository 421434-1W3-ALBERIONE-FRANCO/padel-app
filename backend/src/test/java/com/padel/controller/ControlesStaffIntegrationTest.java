package com.padel.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.padel.domain.entity.TipoBono;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.request.TipoBonoRequest;
import com.padel.dto.response.LoginResponse;
import com.padel.repository.TipoBonoRepository;
import com.padel.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controles del staff contra H2: el catálogo de bonos solo lo administra el ADMIN y queda registrado quién
 * lo cambió; el fichaje es explícito (el login ya no abre la jornada) y no se puede abrir dos veces.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ControlesStaffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private TipoBonoRepository tipoBonoRepository;

    private String token(String email, RolUsuario rol) throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearUsuarioRequest(
                                "Test", "Staff", email, null, "ClaveSegura-2026"))))
                .andExpect(status().isCreated());
        if (rol != RolUsuario.JUGADOR) {
            Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
            usuario.setRol(rol);
            usuarioRepository.save(usuario);
        }
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "ClaveSegura-2026"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readValue(json, LoginResponse.class).token();
    }

    @Test
    void elCatalogoDeBonosLoAdministraElAdminYQuedaRegistradoQuienLoCambio() throws Exception {
        String admin = token("admin.catalogo@test.com", RolUsuario.ADMIN);
        String recepcion = token("recepcion.catalogo@test.com", RolUsuario.RECEPCIONISTA);
        String jugador = token("jugador.catalogo@test.com", RolUsuario.JUGADOR);
        Long adminId = usuarioRepository.findByEmail("admin.catalogo@test.com").orElseThrow().getId();
        String pack = objectMapper.writeValueAsString(
                new TipoBonoRequest("Pack Auditoría", "Prueba", 12, new BigDecimal("9000.00"), 45, null));

        mockMvc.perform(post("/api/v1/tipos-bono").header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON).content(pack))
                .andExpect(status().isForbidden());

        String json = mockMvc.perform(post("/api/v1/tipos-bono").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(pack))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(json).get("id").asLong();

        TipoBono guardado = tipoBonoRepository.findById(id).orElseThrow();
        assertEquals("usuario:" + adminId, guardado.getCreatedBy());
        assertEquals("usuario:" + adminId, guardado.getUpdatedBy());

        // Recepción lo ve para vender y el jugador para saber qué comprar; los dados de baja solo los ve el admin.
        mockMvc.perform(get("/api/v1/tipos-bono").header("Authorization", recepcion))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/tipos-bono/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                new TipoBonoRequest("Pack Auditoría", "Prueba", 12, new BigDecimal("9000.00"), 45, false))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tipos-bono").param("incluirInactivos", "true").header("Authorization", jugador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
        mockMvc.perform(get("/api/v1/tipos-bono").param("incluirInactivos", "true").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty());

        // Un nombre repetido no se acepta.
        mockMvc.perform(post("/api/v1/tipos-bono").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(pack))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elFichajeEsExplicitoYNoSeAbreDosVeces() throws Exception {
        String recepcion = token("recepcion.fichaje@test.com", RolUsuario.RECEPCIONISTA);
        String jugador = token("jugador.fichaje@test.com", RolUsuario.JUGADOR);

        // El login ya no abre la jornada.
        mockMvc.perform(get("/api/v1/fichajes/actual").header("Authorization", recepcion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abierto").value(false));

        mockMvc.perform(post("/api/v1/fichajes").header("Authorization", jugador))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/fichajes").header("Authorization", recepcion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.abierto").value(true));
        mockMvc.perform(post("/api/v1/fichajes").header("Authorization", recepcion))
                .andExpect(status().isConflict());

        String json = mockMvc.perform(put("/api/v1/fichajes/actual").header("Authorization", recepcion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode cerrada = objectMapper.readTree(json);
        assertEquals(false, cerrada.get("abierto").asBoolean());
        assertEquals(0, cerrada.get("minutosTrabajados").asInt());

        mockMvc.perform(put("/api/v1/fichajes/actual").header("Authorization", recepcion))
                .andExpect(status().isConflict());
    }
}
