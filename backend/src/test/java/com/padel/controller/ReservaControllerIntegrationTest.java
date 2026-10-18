package com.padel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.OrigenReserva;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoSuelo;
import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.response.LoginResponse;
import com.padel.repository.CanchaRepository;
import com.padel.repository.FranjaHorariaRepository;
import com.padel.repository.ReservaRepository;
import com.padel.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de GET /api/v1/reservas: con ?fecha= devuelve sólo las reservas de ese día, ordenadas (lo usan
 * la ocupación en vivo y la carga de consumos, que refrescan seguido) y sin parámetro sigue devolviendo
 * todo el historial.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CanchaRepository canchaRepository;

    @Autowired
    private FranjaHorariaRepository franjaHorariaRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    private String tokenDeStaff(String email, RolUsuario rol) throws Exception {
        CrearUsuarioRequest registro = new CrearUsuarioRequest(
                "Test", "Staff", email, null, "password123", RolUsuario.JUGADOR);
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registro)))
                .andExpect(status().isCreated());

        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        usuario.setRol(rol);
        usuarioRepository.save(usuario);

        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, LoginResponse.class).token();
    }

    @Test
    void conFechaDevuelveSoloEseDiaOrdenadoYSinFechaTodoElHistorial() throws Exception {
        String token = tokenDeStaff("recepcion.reservas.dia@test.com", RolUsuario.RECEPCIONISTA);
        Usuario cliente = usuarioRepository.save(Usuario.builder()
                .nombre("Ana").apellido("Gomez").email("cliente.reservas.dia@test.com")
                .passwordHash("hash").rol(RolUsuario.JUGADOR).activo(true).build());
        Cancha cancha = canchaRepository.save(Cancha.builder()
                .nombre("Cancha Reservas Dia").tipoSuelo(TipoSuelo.BLINDEX).techada(true).tieneLuz(true).build());
        FranjaHoraria franja = franjaHorariaRepository.save(FranjaHoraria.builder()
                .cancha(cancha).horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30)).duracionMin(90)
                .precioBase(new BigDecimal("1000")).precioNocturno(new BigDecimal("1200"))
                .diasAplicables("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY").build());
        LocalDate dia = LocalDate.of(2031, 5, 20);
        Reserva tarde = guardar(cliente, cancha, franja, dia, LocalTime.of(20, 0));
        Reserva temprano = guardar(cliente, cancha, franja, dia, LocalTime.of(18, 0));
        Reserva otroDia = guardar(cliente, cancha, franja, dia.plusDays(1), LocalTime.of(18, 0));

        mockMvc.perform(get("/api/v1/reservas").param("fecha", "2031-05-20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(temprano.getId()))
                .andExpect(jsonPath("$[1].id").value(tarde.getId()))
                .andExpect(jsonPath("$[0].fecha").value("2031-05-20"))
                .andExpect(jsonPath("$[0].usuarioEmail").value("cliente.reservas.dia@test.com"))
                .andExpect(jsonPath("$[0].canchaNombre").value("Cancha Reservas Dia"));

        mockMvc.perform(get("/api/v1/reservas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItems(
                        temprano.getId().intValue(), tarde.getId().intValue(), otroDia.getId().intValue())));
    }

    @Test
    void fechaConFormatoInvalidoResponde400() throws Exception {
        String token = tokenDeStaff("admin.reservas.dia@test.com", RolUsuario.ADMIN);

        mockMvc.perform(get("/api/v1/reservas").param("fecha", "20/05/2031")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private Reserva guardar(Usuario usuario, Cancha cancha, FranjaHoraria franja, LocalDate fecha, LocalTime inicio) {
        return reservaRepository.save(Reserva.builder()
                .usuario(usuario).cancha(cancha).franjaHoraria(franja)
                .fecha(fecha).horaInicio(inicio).horaFin(inicio.plusMinutes(90))
                .precioTotal(new BigDecimal("1000")).estadoReserva(EstadoReserva.CONFIRMADA).origen(OrigenReserva.APP)
                .build());
    }
}
