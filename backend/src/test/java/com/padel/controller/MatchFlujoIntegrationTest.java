package com.padel.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.response.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo del match contra la base H2: perfiles, mazo, likes mutuos y contacto liberado.
 * Valida también el mapeo JPA (disponibilidad como colección de embebibles y consultas del mazo).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchFlujoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registrarYLoguear(String nombre, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearUsuarioRequest(
                                nombre, "Match", email, "3510000000", "ClaveSegura-2026"))))
                .andExpect(status().isCreated());
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "ClaveSegura-2026"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readValue(json, LoginResponse.class).token();
    }

    private void guardarPerfil(String token, String categoria, String posicion, List<Map<String, String>> disponibilidad) throws Exception {
        Map<String, Object> perfil = Map.of(
                "categoria", categoria,
                "posicion", posicion,
                "bio", "Busco pareja",
                "disponibilidad", disponibilidad);
        mockMvc.perform(put("/api/v1/jugadores/me/perfil")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(perfil)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value(categoria));
    }

    private static Map<String, String> bloque(String dia, String bloque) {
        return Map.of("diaSemana", dia, "bloque", bloque);
    }

    private String swipe(long usuarioId, String tipo) throws Exception {
        return objectMapper.writeValueAsString(Map.of("usuarioId", usuarioId, "tipo", tipo));
    }

    private long usuarioId(String token) throws Exception {
        String json = mockMvc.perform(get("/api/v1/usuarios/me").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    @Test
    void dosJugadoresCompatiblesHacenMatchYVenElContactoDelOtro() throws Exception {
        String ana = registrarYLoguear("Ana", "ana.match@test.com");
        String beto = registrarYLoguear("Beto", "beto.match@test.com");
        String carla = registrarYLoguear("Carla", "carla.match@test.com");

        // Sin perfil no hay mazo.
        mockMvc.perform(get("/api/v1/match/candidatos").header("Authorization", ana))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/jugadores/me/perfil").header("Authorization", ana))
                .andExpect(status().isNotFound());

        guardarPerfil(ana, "CUARTA", "DRIVE", List.of(bloque("TUESDAY", "NOCHE"), bloque("SATURDAY", "MANANA")));
        guardarPerfil(beto, "QUINTA", "REVES", List.of(bloque("TUESDAY", "NOCHE")));
        // Carla es primera: tres categorías de distancia, no aparece en el mazo de Ana.
        guardarPerfil(carla, "PRIMERA", "REVES", List.of(bloque("TUESDAY", "NOCHE")));

        long anaId = usuarioId(ana);
        long betoId = usuarioId(beto);

        String mazo = mockMvc.perform(get("/api/v1/match/candidatos").header("Authorization", ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Beto M."))
                .andExpect(jsonPath("$[0].coincidencias", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        JsonNode candidato = objectMapper.readTree(mazo).get(0);
        // En el mazo no se expone el contacto.
        assertFalse(candidato.has("email"));
        assertFalse(candidato.has("telefono"));

        mockMvc.perform(post("/api/v1/match/swipes").header("Authorization", ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(swipe(betoId, "LIKE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.match").value(false));

        // Ya respondido: sale del mazo de Ana y no se puede repetir.
        mockMvc.perform(get("/api/v1/match/candidatos").header("Authorization", ana))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(post("/api/v1/match/swipes").header("Authorization", ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(swipe(betoId, "PASS")))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/match/swipes").header("Authorization", beto)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(swipe(anaId, "LIKE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.match").value(true))
                .andExpect(jsonPath("$.pareja.email").value("ana.match@test.com"))
                .andExpect(jsonPath("$.pareja.telefono").value("3510000000"));

        mockMvc.perform(get("/api/v1/match/parejas").header("Authorization", ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Beto Match"))
                .andExpect(jsonPath("$[0].email").value("beto.match@test.com"))
                .andExpect(jsonPath("$[0].coincidencias[0].diaSemana").value("TUESDAY"));

        // Sin canchas cargadas no hay turnos para sugerir, pero el endpoint responde; un tercero no ve el match.
        String parejas = mockMvc.perform(get("/api/v1/match/parejas").header("Authorization", beto))
                .andReturn().getResponse().getContentAsString();
        long matchId = objectMapper.readTree(parejas).get(0).get("id").asLong();
        mockMvc.perform(get("/api/v1/match/parejas/" + matchId + "/turnos-sugeridos").header("Authorization", beto))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/match/parejas/" + matchId + "/turnos-sugeridos").header("Authorization", carla))
                .andExpect(status().isNotFound());

        // Los dos recibieron el aviso del match.
        mockMvc.perform(get("/api/v1/notificaciones").header("Authorization", ana))
                .andExpect(jsonPath("$[0].tipo").value("MATCH_NUEVO"));
    }

    @Test
    void elPerfilExigeAlMenosUnHorario() throws Exception {
        String dani = registrarYLoguear("Dani", "dani.match@test.com");

        mockMvc.perform(put("/api/v1/jugadores/me/perfil")
                        .header("Authorization", dani)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "categoria", "CUARTA", "posicion", "DRIVE", "disponibilidad", List.of()))))
                .andExpect(status().isBadRequest());
    }
}
