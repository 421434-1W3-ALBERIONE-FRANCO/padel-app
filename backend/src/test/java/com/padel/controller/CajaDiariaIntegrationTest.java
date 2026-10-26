package com.padel.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.TipoBono;
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
import com.padel.repository.TipoBonoRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.RedisLockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobros del mostrador hasta la caja del día, contra H2: turno cobrado en el acto, turno para cobrar al
 * llegar (pendiente en la caja hasta que se cobra), venta de bono y los rechazos. Como otros tests comparten
 * la base, la caja se compara contra la lectura inicial y los movimientos se buscan por id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CajaDiariaIntegrationTest {

    @Autowired
    private TipoBonoRepository tipoBonoRepository;
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

    // Confirmar libera el lock del horario en Redis: acá no hace falta un Redis real.
    @MockBean
    private RedisLockService redisLockService;

    private String token(String email, RolUsuario rol) throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearUsuarioRequest(
                                "Test", "Caja", email, null, "ClaveSegura-2026"))))
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

    private JsonNode caja(String token, LocalDate fecha) throws Exception {
        String json = mockMvc.perform(get("/api/v1/reportes/caja-diaria").param("fecha", fecha.toString())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                // Sin charset en la respuesta, MockMvc decodifica como ISO-8859-1 y rompe los acentos de los nombres.
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }

    private String cobro(String metodo) throws Exception {
        return objectMapper.writeValueAsString(Map.of("metodo", metodo));
    }

    private static boolean contiene(JsonNode lista, String campo, long id) {
        for (JsonNode item : lista) {
            if (item.get(campo).asLong() == id) return true;
        }
        return false;
    }

    private static JsonNode buscar(JsonNode lista, String campo, long id) {
        for (JsonNode item : lista) {
            if (item.get(campo).asLong() == id) return item;
        }
        throw new AssertionError("No está el " + campo + " " + id);
    }

    @Test
    void losCobrosDelMostradorYLaVentaDeBonosLleganALaCajaDelDia() throws Exception {
        String recepcion = token("recepcion.caja@test.com", RolUsuario.RECEPCIONISTA);
        // El bono lo vende un admin: con recepción se consultan las comisiones, cuya configuración H2 no tiene.
        String admin = token("admin.caja@test.com", RolUsuario.ADMIN);
        String jugador = token("jugador.caja@test.com", RolUsuario.JUGADOR);
        LocalDate hoy = LocalDate.now();

        Usuario cliente = usuarioRepository.save(Usuario.builder()
                .nombre("Lucía").apellido("Caja").email("cliente.caja@test.com")
                .passwordHash("hash").rol(RolUsuario.JUGADOR).activo(true).build());
        Cancha cancha = canchaRepository.save(Cancha.builder()
                .nombre("Cancha Caja").tipoSuelo(TipoSuelo.BLINDEX).techada(true).tieneLuz(true).build());
        FranjaHoraria franja = franjaHorariaRepository.save(FranjaHoraria.builder()
                .cancha(cancha).horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30)).duracionMin(90)
                .precioBase(new BigDecimal("1000")).precioNocturno(new BigDecimal("1000"))
                .diasAplicables("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY").build());
        Reserva cobradaEnElActo = reservaRepository.save(reserva(cliente, cancha, franja, hoy, LocalTime.of(18, 0)));
        Reserva paraCobrarAlLlegar = reservaRepository.save(reserva(cliente, cancha, franja, hoy, LocalTime.of(20, 0)));

        JsonNode antes = caja(recepcion, hoy);
        BigDecimal ingresosAntes = antes.get("totalIngresos").decimalValue();
        BigDecimal efectivoAntes = antes.get("totalEfectivo").decimalValue();

        // 1) Turno cobrado en efectivo en el mostrador: queda el pago y la reserva se confirma.
        String pagoJson = mockMvc.perform(post("/api/v1/reservas/" + cobradaEnElActo.getId() + "/cobro-mostrador")
                        .header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON).content(cobro("EFECTIVO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metodo").value("EFECTIVO"))
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.concepto").value("TURNO"))
                .andReturn().getResponse().getContentAsString();
        long pagoTurno = objectMapper.readTree(pagoJson).get("id").asLong();
        assertEquals(EstadoReserva.CONFIRMADA, reservaRepository.findById(cobradaEnElActo.getId()).orElseThrow().getEstadoReserva());

        // No se cobra dos veces, en el mostrador no se cobra online y un jugador no puede registrar cobros.
        mockMvc.perform(post("/api/v1/reservas/" + cobradaEnElActo.getId() + "/cobro-mostrador")
                        .header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON).content(cobro("TARJETA")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/reservas/" + paraCobrarAlLlegar.getId() + "/cobro-mostrador")
                        .header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON).content(cobro("MERCADOPAGO")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/reservas/" + paraCobrarAlLlegar.getId() + "/cobro-mostrador")
                        .header("Authorization", jugador)
                        .contentType(MediaType.APPLICATION_JSON).content(cobro("EFECTIVO")))
                .andExpect(status().isForbidden());

        // 2) Turno confirmado para pagar al llegar: sin pago, aparece pendiente en la caja del día.
        mockMvc.perform(put("/api/v1/reservas/" + paraCobrarAlLlegar.getId() + "/confirmar").header("Authorization", recepcion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoReserva").value("CONFIRMADA"));
        JsonNode conPendiente = caja(recepcion, hoy);
        JsonNode pendiente = buscar(conPendiente.get("pendientesDeCobro"), "reservaId", paraCobrarAlLlegar.getId());
        assertEquals("Lucía Caja", pendiente.get("cliente").asText());
        assertEquals(0, new BigDecimal("1000").compareTo(pendiente.get("monto").decimalValue()));
        assertFalse(contiene(conPendiente.get("pendientesDeCobro"), "reservaId", cobradaEnElActo.getId()));

        // 3) Venta de un bono con tarjeta: entra a la caja; sin medio de pago no se puede vender.
        // Con H2 no corre la migración que carga el catálogo: el pack se da de alta acá.
        TipoBono pack = tipoBonoRepository.findAll().stream().filter(t -> "10HS".equals(t.getNombre())).findFirst()
                .orElseGet(() -> tipoBonoRepository.save(TipoBono.builder().nombre("10HS").horas(10)
                        .precio(new BigDecimal("8000.00")).diasVigencia(30).activo(true).build()));
        Map<String, Object> bono = Map.of("usuarioEmail", "cliente.caja@test.com", "tipoBonoId", pack.getId(), "metodo", "TARJETA");
        mockMvc.perform(post("/api/v1/bonos").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(bono)))
                .andExpect(status().isCreated());
        Map<String, Object> bonoSinMetodo = Map.of("usuarioEmail", "cliente.caja@test.com", "tipoBonoId", pack.getId());
        mockMvc.perform(post("/api/v1/bonos").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(bonoSinMetodo)))
                .andExpect(status().isBadRequest());

        // 4) Llega el cliente y paga con tarjeta: deja de estar pendiente.
        mockMvc.perform(post("/api/v1/reservas/" + paraCobrarAlLlegar.getId() + "/cobro-mostrador")
                        .header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON).content(cobro("TARJETA")))
                .andExpect(status().isCreated());

        JsonNode despues = caja(recepcion, hoy);
        assertEquals(0, ingresosAntes.add(new BigDecimal("10000")).compareTo(despues.get("totalIngresos").decimalValue()),
                "1000 del turno en efectivo + 8000 del bono + 1000 del turno con tarjeta");
        assertEquals(0, efectivoAntes.add(new BigDecimal("1000")).compareTo(despues.get("totalEfectivo").decimalValue()));
        assertFalse(contiene(despues.get("pendientesDeCobro"), "reservaId", paraCobrarAlLlegar.getId()));

        JsonNode movimientoTurno = buscar(despues.get("movimientos"), "pagoId", pagoTurno);
        assertEquals("TURNO", movimientoTurno.get("concepto").asText());
        assertEquals("Lucía Caja", movimientoTurno.get("cliente").asText());
        assertEquals("Test Caja", movimientoTurno.get("atendidoPor").asText());
        assertTrue(movimientoTurno.get("detalle").asText().startsWith("Turno · Cancha Caja"));

        boolean hayVentaDeBono = false;
        for (JsonNode movimiento : despues.get("movimientos")) {
            if ("BONO".equals(movimiento.get("concepto").asText()) && "TARJETA".equals(movimiento.get("metodo").asText())
                    && movimiento.get("cliente").asText().equals("Lucía Caja")) {
                hayVentaDeBono = true;
                assertEquals("Bono 10HS · 10 hs", movimiento.get("detalle").asText());
            }
        }
        assertTrue(hayVentaDeBono, "La venta del bono aparece en los movimientos");
    }

    private static Reserva reserva(Usuario usuario, Cancha cancha, FranjaHoraria franja, LocalDate fecha, LocalTime inicio) {
        return Reserva.builder()
                .usuario(usuario).cancha(cancha).franjaHoraria(franja)
                .fecha(fecha).horaInicio(inicio).horaFin(inicio.plusMinutes(90))
                .precioTotal(new BigDecimal("1000")).estadoReserva(EstadoReserva.PENDIENTE_PAGO).origen(OrigenReserva.RECEPCION)
                .build();
    }
}
