package com.padel.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.response.LoginResponse;
import com.padel.util.ImagenPerfilProcessorTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Foto de perfil contra H2: carga, quién la ve, versión en el mazo, rechazos y borrado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FotoPerfilIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String registrarYLoguear(String nombre, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearUsuarioRequest(
                                nombre, "Foto", email, "3510000000", "ClaveSegura-2026"))))
                .andExpect(status().isCreated());
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "ClaveSegura-2026"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readValue(json, LoginResponse.class).token();
    }

    private void guardarPerfil(String token, String posicion, boolean visible) throws Exception {
        // Octava y domingo a la tarde: lejos de los jugadores de los otros tests, que comparten la base H2.
        Map<String, Object> perfil = Map.of(
                "categoria", "OCTAVA", "posicion", posicion, "visibleEnMatch", visible,
                "disponibilidad", List.of(Map.of("diaSemana", "SUNDAY", "bloque", "TARDE")));
        mockMvc.perform(put("/api/v1/jugadores/me/perfil").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(perfil)))
                .andExpect(status().isOk());
    }

    private long usuarioId(String token) throws Exception {
        String json = mockMvc.perform(get("/api/v1/usuarios/me").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    @Test
    void cargarVerYQuitarLaFotoDePerfil() throws Exception {
        String ana = registrarYLoguear("Ana", "ana.foto@test.com");
        String beto = registrarYLoguear("Beto", "beto.foto@test.com");
        long anaId = usuarioId(ana);
        byte[] png = ImagenPerfilProcessorTest.imagen(900, 700, "png");

        // Sin perfil guardado todavía no se puede cargar la foto.
        mockMvc.perform(put("/api/v1/jugadores/me/perfil/foto").header("Authorization", ana)
                        .contentType(MediaType.IMAGE_PNG).content(png))
                .andExpect(status().isConflict());

        guardarPerfil(ana, "DRIVE", true);
        mockMvc.perform(put("/api/v1/jugadores/me/perfil/foto").header("Authorization", ana)
                        .contentType(MediaType.IMAGE_PNG).content(png))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoVersion", notNullValue()));

        // La propia, recortada a 512x512 y en JPEG.
        byte[] jpeg = mockMvc.perform(get("/api/v1/jugadores/" + anaId + "/foto").header("Authorization", ana))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage foto = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertEquals(512, foto.getWidth());
        assertEquals(512, foto.getHeight());

        // Beto la ve porque Ana está visible en el match, y en su mazo viene la versión de la foto.
        mockMvc.perform(get("/api/v1/jugadores/" + anaId + "/foto").header("Authorization", beto))
                .andExpect(status().isOk());
        guardarPerfil(beto, "REVES", true);
        mockMvc.perform(get("/api/v1/match/candidatos").header("Authorization", beto))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fotoVersion", notNullValue()));

        // Si Ana se oculta del match, Beto (sin match con ella) deja de verla; ella sí.
        guardarPerfil(ana, "DRIVE", false);
        mockMvc.perform(get("/api/v1/jugadores/" + anaId + "/foto").header("Authorization", beto))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/jugadores/" + anaId + "/foto").header("Authorization", ana))
                .andExpect(status().isOk());

        // Rechazos: no es una imagen, otro Content-Type, más de 1 MB.
        mockMvc.perform(put("/api/v1/jugadores/me/perfil/foto").header("Authorization", ana)
                        .contentType(MediaType.IMAGE_JPEG).content("hola".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/jugadores/me/perfil/foto").header("Authorization", ana)
                        .contentType(MediaType.TEXT_PLAIN).content(png))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(put("/api/v1/jugadores/me/perfil/foto").header("Authorization", ana)
                        .contentType(MediaType.IMAGE_JPEG).content(new byte[1_000_001]))
                .andExpect(status().isBadRequest());

        // Al quitarla, el perfil vuelve sin versión y la foto ya no existe.
        String perfil = mockMvc.perform(delete("/api/v1/jugadores/me/perfil/foto").header("Authorization", ana))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode sinFoto = objectMapper.readTree(perfil);
        assertEquals(false, sinFoto.hasNonNull("fotoVersion"));
        mockMvc.perform(get("/api/v1/jugadores/" + anaId + "/foto").header("Authorization", ana))
                .andExpect(status().isNotFound());
    }
}
