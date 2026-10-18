package com.padel.repository;

import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.OrigenReserva;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoSuelo;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ReservaRepositoryTest {

    @Autowired
    private ReservaRepository reservaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private CanchaRepository canchaRepository;
    @Autowired
    private FranjaHorariaRepository franjaHorariaRepository;
    @Autowired
    private TestEntityManager entityManager;

    private Usuario usuario;
    private Cancha cancha;
    private FranjaHoraria franja;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .nombre("Juan").apellido("Perez").email("juan.repo@test.com")
                .passwordHash("hash").rol(RolUsuario.JUGADOR).activo(true).build());
        cancha = canchaRepository.save(Cancha.builder()
                .nombre("Cancha Repo").tipoSuelo(TipoSuelo.BLINDEX).techada(true).tieneLuz(true).build());
        franja = franjaHorariaRepository.save(FranjaHoraria.builder()
                .cancha(cancha).horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30)).duracionMin(90)
                .precioBase(new BigDecimal("1000")).precioNocturno(new BigDecimal("1200"))
                .diasAplicables("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY").build());
    }

    @Test
    void reservasQueOcupanLaCancha_IncluyenLasPendientesDePagoYExcluyenLasCanceladas() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        Reserva pendiente = guardar(cancha, fecha, LocalTime.of(18, 0), EstadoReserva.PENDIENTE_PAGO);
        Reserva confirmada = guardar(cancha, fecha, LocalTime.of(20, 0), EstadoReserva.CONFIRMADA);
        guardar(cancha, fecha, LocalTime.of(10, 0), EstadoReserva.CANCELADA);

        List<Reserva> ocupan = reservaRepository.findActiveReservationsByCanchaAndDate(cancha.getId(), fecha);

        assertThat(ocupan).extracting(Reserva::getId)
                .containsExactlyInAnyOrder(pendiente.getId(), confirmada.getId());
    }

    @Test
    void confirmadasFinalizadas_SoloLasConfirmadasCuyoTurnoYaTermino() {
        LocalDate hoy = LocalDate.of(2026, 3, 10);
        LocalTime ahora = LocalTime.of(20, 0);
        Reserva deAyer = guardar(cancha, hoy.minusDays(1), LocalTime.of(22, 0), EstadoReserva.CONFIRMADA);
        Reserva terminaJustoAhora = guardar(cancha, hoy, LocalTime.of(18, 30), EstadoReserva.CONFIRMADA); // 18:30 a 20:00
        guardar(cancha, hoy, LocalTime.of(19, 0), EstadoReserva.CONFIRMADA);             // en juego hasta las 20:30
        guardar(cancha, hoy.plusDays(1), LocalTime.of(10, 0), EstadoReserva.CONFIRMADA); // todavía no se jugó
        guardar(cancha, hoy.minusDays(1), LocalTime.of(10, 0), EstadoReserva.PENDIENTE_PAGO);
        guardar(cancha, hoy.minusDays(1), LocalTime.of(12, 0), EstadoReserva.CANCELADA);
        guardar(cancha, hoy.minusDays(2), LocalTime.of(12, 0), EstadoReserva.COMPLETADA);

        List<Reserva> finalizadas = reservaRepository.findConfirmadasFinalizadas(hoy, ahora);

        assertThat(finalizadas).extracting(Reserva::getId)
                .containsExactlyInAnyOrder(deAyer.getId(), terminaJustoAhora.getId());
    }

    @Test
    void delDia_TraeTodosLosEstadosDeEseDiaOrdenadosPorHoraYCanchaConUsuarioYCanchaCargados() {
        Cancha otraCancha = canchaRepository.save(Cancha.builder()
                .nombre("A Cancha Repo").tipoSuelo(TipoSuelo.CEMENTO).techada(false).tieneLuz(true).build());
        LocalDate dia = LocalDate.of(2026, 3, 10);
        Reserva tarde = guardar(cancha, dia, LocalTime.of(20, 0), EstadoReserva.CONFIRMADA);
        Reserva tempranoCanchaRepo = guardar(cancha, dia, LocalTime.of(18, 0), EstadoReserva.CANCELADA);
        Reserva tempranoOtraCancha = guardar(otraCancha, dia, LocalTime.of(18, 0), EstadoReserva.PENDIENTE_PAGO);
        guardar(cancha, dia.plusDays(1), LocalTime.of(18, 0), EstadoReserva.CONFIRMADA);
        // Se vacía el contexto de persistencia para que la consulta lea de la base y se note si falta el fetch.
        entityManager.flush();
        entityManager.clear();

        List<Reserva> delDia = reservaRepository.findDelDia(dia);

        assertThat(delDia).extracting(Reserva::getId)
                .containsExactly(tempranoOtraCancha.getId(), tempranoCanchaRepo.getId(), tarde.getId());
        assertThat(delDia).allSatisfy(r -> {
            assertThat(Hibernate.isInitialized(r.getUsuario())).isTrue();
            assertThat(Hibernate.isInitialized(r.getCancha())).isTrue();
        });
    }

    private Reserva guardar(Cancha cancha, LocalDate fecha, LocalTime inicio, EstadoReserva estado) {
        return reservaRepository.save(Reserva.builder()
                .usuario(usuario).cancha(cancha).franjaHoraria(franja)
                .fecha(fecha).horaInicio(inicio).horaFin(inicio.plusMinutes(90))
                .precioTotal(new BigDecimal("1000")).estadoReserva(estado).origen(OrigenReserva.APP)
                .build());
    }
}
