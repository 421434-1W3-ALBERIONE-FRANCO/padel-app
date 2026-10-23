package com.padel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padel.domain.entity.MensajeSaliente;
import com.padel.domain.enums.CanalMensaje;
import com.padel.dto.request.CambiarPasswordRequest;
import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.request.VerificarEmailRequest;
import com.padel.dto.response.LoginResponse;
import com.padel.repository.MensajeSalienteRepository;
import com.padel.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autenticación de punta a punta con la verificación de email y los límites encendidos: registro que exige
 * confirmar el email (el enlace se lee de la bandeja de salida), logout que revoca el token, cambio de
 * contraseña que cierra las sesiones, bloqueo por intentos y emails sin distinguir mayúsculas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.auth.verificacion-email-obligatoria=true",
        "app.limites.habilitado=true",
        "app.limites.login.maximo=1000",
        "app.limites.registro.maximo=1000",
        "app.limites.recuperacion.maximo=2",
        "app.limites.recuperacion.ventana=PT1H"
})
class AutenticacionSeguridadIntegrationTest {

    private static final String CLAVE = "Cancha-Segura-2026";
    private static final Pattern TOKEN_EN_ENLACE = Pattern.compile("verificar-email\\?token=([A-Za-z0-9_%\\-]+)");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MensajeSalienteRepository mensajes;
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void elRegistroExigeConfirmarElEmailAntesDeIniciarSesion() throws Exception {
        String email = "verificacion.flujo@test.com";
        registrar(email);

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, CLAVE))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("EMAIL_NO_VERIFICADO"));

        String token = tokenDeVerificacion(email);
        mockMvc.perform(post("/api/v1/auth/verificacion-email").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VerificarEmailRequest(token))))
                .andExpect(status().isNoContent());
        // El enlace es de un solo uso.
        mockMvc.perform(post("/api/v1/auth/verificacion-email").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VerificarEmailRequest(token))))
                .andExpect(status().isBadRequest());

        login(email);
    }

    @Test
    void elLogoutRevocaElTokenAunqueNoHayaVencido() throws Exception {
        String email = "logout.revoca@test.com";
        String token = registrarVerificarYEntrar(email);

        mockMvc.perform(get("/api/v1/usuarios/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/usuarios/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarLaContrasenaCierraTodasLasSesiones() throws Exception {
        String email = "cambio.clave@test.com";
        String token = registrarVerificarYEntrar(email);

        mockMvc.perform(put("/api/v1/usuarios/me/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CambiarPasswordRequest(CLAVE, "Otra-Clave-Segura-2026"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/usuarios/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void unaContrasenaComunOCortaSeRechazaAlRegistrarse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CrearUsuarioRequest("Ana", "Lopez", "clave.debil@test.com", null, "password123"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CrearUsuarioRequest("Ana", "Lopez", "clave.corta@test.com", null, "abc123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elTelefonoEsOpcionalAunqueElFormularioLoMandeVacio() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearUsuarioRequest(
                                "Sin", "Telefono", "sin.telefono@test.com", "", "Cancha-Segura-2026"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearUsuarioRequest(
                                "Mal", "Telefono", "mal.telefono@test.com", "llamame", "Cancha-Segura-2026"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elEmailNoDistingueMayusculas() throws Exception {
        registrar("Mixto.Mayusculas@Test.com");
        assertThat(usuarioRepository.findByEmail("mixto.mayusculas@test.com")).isPresent();

        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CrearUsuarioRequest("Otro", "Usuario", "MIXTO.MAYUSCULAS@TEST.COM", null, CLAVE))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elLimitePorIpRespondeDemasiadosPedidosConRetryAfter() throws Exception {
        String cuerpo = "{\"email\":\"limite.ip@test.com\"}";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/recuperar-password").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                            .with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/auth/recuperar-password").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"));
        // Otra IP no queda afectada.
        mockMvc.perform(post("/api/v1/auth/recuperar-password").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .with(r -> { r.setRemoteAddr("203.0.113.8"); return r; }))
                .andExpect(status().isOk());
    }

    @Test
    void variosIntentosFallidosBloqueanLaCuentaTemporalmente() throws Exception {
        String email = "bloqueo.intentos@test.com";
        registrarVerificarYEntrar(email);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest(email, "Clave-Equivocada-1"))))
                    .andExpect(status().isUnauthorized());
        }
        // Incluso con la contraseña correcta, hasta que pase la ventana.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, CLAVE))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"));
    }

    private String registrarVerificarYEntrar(String email) throws Exception {
        registrar(email);
        mockMvc.perform(post("/api/v1/auth/verificacion-email").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VerificarEmailRequest(tokenDeVerificacion(email)))))
                .andExpect(status().isNoContent());
        return login(email);
    }

    private void registrar(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CrearUsuarioRequest("Test", "Seguridad", email, null, CLAVE))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerificado").value(false));
    }

    private String login(String email) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(respuesta, LoginResponse.class).token();
    }

    /** El email de verificación quedó en la bandeja de salida (outbox): de ahí se saca el enlace. */
    private String tokenDeVerificacion(String email) {
        MensajeSaliente mensaje = mensajes.findAll().stream()
                .filter(m -> m.getCanal() == CanalMensaje.EMAIL && m.getDestinatario().equals(email.toLowerCase()))
                .filter(m -> m.getAsunto().startsWith("Confirmá tu email"))
                .max(Comparator.comparing(MensajeSaliente::getId))
                .orElseThrow();
        Matcher matcher = TOKEN_EN_ENLACE.matcher(mensaje.getCuerpo());
        assertThat(matcher.find()).isTrue();
        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    private String json(Object valor) throws Exception {
        return objectMapper.writeValueAsString(valor);
    }
}
