package com.padel.repository;

import com.padel.domain.enums.OrigenReserva;
import com.padel.dto.request.ReservaRequest;
import com.padel.exception.SlotNoDisponibleException;
import com.padel.service.ReservaService;
import com.padel.support.ConPostgresReal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Corre sobre PostgreSQL real: las migraciones aplican limpias (incluido el seed de desarrollo), el esquema
 * coincide con las entidades y las restricciones nuevas protegen los datos aunque falle la aplicación.
 */
class MigracionesPostgresTest extends ConPostgresReal {

    private static final LocalDate MANIANA = LocalDate.now().plusDays(1);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReservaService reservaService;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM reservas WHERE fecha = ?", MANIANA);
        jdbc.update("UPDATE canchas SET duraciones_permitidas = '90'");
    }

    @Test
    void todasLasMigracionesAplicanSinErrores() {
        Integer fallidas = jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        assertThat(fallidas).isZero();
    }

    @Test
    void laBaseRechazaDosReservasVigentesSuperpuestasEnLaMismaCancha() {
        long cancha = idCancha("Cancha 2%");
        insertarReserva(cancha, franja(cancha, "17:00"), "17:00", "20:00", "CONFIRMADA");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertarReserva(cancha, franja(cancha, "18:30"), "18:30", "20:00", "PENDIENTE_PAGO"));
        // Una cancelada no ocupa la cancha: no choca.
        insertarReserva(cancha, franja(cancha, "18:30"), "18:30", "20:00", "CANCELADA");
    }

    @Test
    void laBaseRechazaUnaReservaQuePasaLaMedianoche() {
        long cancha = idCancha("Cancha 1%");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertarReserva(cancha, franja(cancha, "21:30"), "21:30", "00:30", "PENDIENTE_PAGO"));
    }

    @Test
    void dosAltasSimultaneasQueSeSuperponenDejanUnaSola() throws Exception {
        long cancha = idCancha("Cancha 3%");
        jdbc.update("UPDATE canchas SET duraciones_permitidas = '90,180' WHERE id = ?", cancha);
        // 17:00-20:00 y 18:30-20:00: distinta hora de inicio (el lock de Redis no las distinguía).
        ReservaRequest larga = new ReservaRequest(cancha, franja(cancha, "17:00"), MANIANA, OrigenReserva.APP, 180, null);
        ReservaRequest corta = new ReservaRequest(cancha, franja(cancha, "18:30"), MANIANA, OrigenReserva.APP, 90, null);

        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> resultados = new ArrayList<>();
            resultados.add(hilos.submit(intento(largada, larga, "cliente@padelapp.com")));
            resultados.add(hilos.submit(intento(largada, corta, "cliente2@padelapp.com")));
            largada.countDown();

            int exitos = 0;
            int rechazos = 0;
            for (Future<Object> resultado : resultados) {
                try {
                    resultado.get(30, TimeUnit.SECONDS);
                    exitos++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(SlotNoDisponibleException.class);
                    rechazos++;
                }
            }
            assertThat(exitos).isEqualTo(1);
            assertThat(rechazos).isEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
        Integer vigentes = jdbc.queryForObject(
                "SELECT count(*) FROM reservas WHERE cancha_id = ? AND fecha = ? AND estado_reserva IN ('PENDIENTE_PAGO','CONFIRMADA')",
                Integer.class, cancha, MANIANA);
        assertThat(vigentes).isEqualTo(1);
    }

    private Callable<Object> intento(CountDownLatch largada, ReservaRequest request, String email) {
        return () -> {
            largada.await();
            return reservaService.crearReserva(request, email);
        };
    }

    private long idCancha(String patron) {
        return jdbc.queryForObject("SELECT id FROM canchas WHERE nombre LIKE ?", Long.class, patron);
    }

    private long franja(long cancha, String horaInicio) {
        return jdbc.queryForObject("SELECT id FROM franjas_horarias WHERE cancha_id = ? AND hora_inicio = ?::time",
                Long.class, cancha, horaInicio);
    }

    private void insertarReserva(long cancha, long franja, String inicio, String fin, String estado) {
        Long usuario = jdbc.queryForObject("SELECT id FROM usuarios WHERE email = 'cliente@padelapp.com'", Long.class);
        jdbc.update("INSERT INTO reservas (usuario_id, cancha_id, franja_id, fecha, hora_inicio, hora_fin, precio_total, "
                        + "estado_reserva, origen, created_at) VALUES (?, ?, ?, ?, ?::time, ?::time, 1000, ?, 'APP', now())",
                usuario, cancha, franja, MANIANA, inicio, fin, estado);
    }
}
