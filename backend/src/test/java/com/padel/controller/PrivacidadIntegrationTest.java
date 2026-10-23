package com.padel.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.TipoSolicitud;
import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.request.PostulacionSolicitudRequest;
import com.padel.dto.request.SolicitudPartidoRequest;
import com.padel.dto.response.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los emails de los jugadores ya no se exponen en el tablón ni en el ranking: el contacto aparece solo
 * cuando hay acuerdo (postulación aceptada) y solo para las partes involucradas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrivacidadIntegrationTest {

    private static final String CLAVE = "Cancha-Segura-2026";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void elTablonNoExponeEmailsYElContactoSoloLlegaAlPostulanteAceptado() throws Exception {
        String creador = token("creador.tablon@test.com", "Carla");
        String postulante = token("postulante.tablon@test.com", "Pablo");
        String curioso = token("curioso.tablon@test.com", "Cecilia");

        String creada = mockMvc.perform(post("/api/v1/solicitudes-partido").header("Authorization", "Bearer " + creador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SolicitudPartidoRequest(TipoSolicitud.BUSCA_PAREJA,
                                CategoriaJugador.CUARTA, 1, LocalDateTime.now().plusDays(2), null, "Partido amistoso"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json(creada).get("id").asLong();

        String postulada = mockMvc.perform(post("/api/v1/solicitudes-partido/" + id + "/postulaciones")
                        .header("Authorization", "Bearer " + postulante).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostulacionSolicitudRequest("¡Me sumo!"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long postulacionId = json(postulada).get("postulaciones").get(0).get("id").asLong();

        // Un tercero: ni emails ni las postulaciones de otros.
        String vistaCurioso = mockMvc.perform(get("/api/v1/solicitudes-partido/" + id).header("Authorization", "Bearer " + curioso))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(vistaCurioso).doesNotContain("@test.com");
        assertThat(json(vistaCurioso).get("postulaciones")).isEmpty();
        assertThat(json(vistaCurioso).get("cantidadPostulaciones").asInt()).isEqualTo(1);

        // Antes de aceptar, el postulante tampoco ve el contacto del creador.
        assertThat(mockMvc.perform(get("/api/v1/solicitudes-partido/" + id).header("Authorization", "Bearer " + postulante))
                .andReturn().getResponse().getContentAsString()).doesNotContain("creador.tablon@test.com");

        mockMvc.perform(put("/api/v1/solicitudes-partido/" + id + "/postulaciones/" + postulacionId + "/aceptar")
                        .header("Authorization", "Bearer " + creador))
                .andExpect(status().isOk());

        JsonNode vistaPostulante = json(mockMvc.perform(get("/api/v1/solicitudes-partido/" + id).header("Authorization", "Bearer " + postulante))
                .andReturn().getResponse().getContentAsString());
        assertThat(vistaPostulante.get("creadorContacto").get("email").asText()).isEqualTo("creador.tablon@test.com");

        JsonNode vistaCreador = json(mockMvc.perform(get("/api/v1/solicitudes-partido/" + id).header("Authorization", "Bearer " + creador))
                .andReturn().getResponse().getContentAsString());
        assertThat(vistaCreador.get("postulaciones").get(0).get("contacto").get("email").asText()).isEqualTo("postulante.tablon@test.com");

        // Y el tercero sigue sin ver nada.
        assertThat(mockMvc.perform(get("/api/v1/solicitudes-partido").header("Authorization", "Bearer " + curioso).param("estado", "COMPLETA"))
                .andReturn().getResponse().getContentAsString()).doesNotContain("@test.com");
    }

    @Test
    void elRankingNoExponeEmails() throws Exception {
        String token = token("ranking.privado@test.com", "Rita");

        mockMvc.perform(get("/api/v1/rankings/CUARTA").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("jugadorEmail"));
    }

    private String token(String email, String nombre) throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearUsuarioRequest(nombre, "Privacidad", email, null, CLAVE))))
                .andExpect(status().isCreated());
        String respuesta = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, CLAVE))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(respuesta, LoginResponse.class).token();
    }

    private JsonNode json(String contenido) throws Exception {
        return objectMapper.readTree(contenido);
    }
}
