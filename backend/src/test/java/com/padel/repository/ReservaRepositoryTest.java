package com.padel.repository;

import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.OrigenReserva;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoSuelo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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

    @Test
    void reservasQueOcupanLaCancha_IncluyenLasPendientesDePagoYExcluyenLasCanceladas() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nombre("Juan").apellido("Perez").email("juan.repo@test.com")
                .passwordHash("hash").rol(RolUsuario.JUGADOR).activo(true).build());
        Cancha cancha = canchaRepository.save(Cancha.builder()
                .nombre("Cancha Repo").tipoSuelo(TipoSuelo.BLINDEX).techada(true).tieneLuz(true).build());
        FranjaHoraria franja = franjaHorariaRepository.save(FranjaHoraria.builder()
                .cancha(cancha).horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30)).duracionMin(90)
                .precioBase(new BigDecimal("1000")).precioNocturno(new BigDecimal("1200"))
                .diasAplicables("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY").build());

        LocalDate fecha = LocalDate.now().plusDays(1);
        Reserva pendiente = guardar(usuario, cancha, franja, fecha, LocalTime.of(18, 0), EstadoReserva.PENDIENTE_PAGO);
        Reserva confirmada = guardar(usuario, cancha, franja, fecha, LocalTime.of(20, 0), EstadoReserva.CONFIRMADA);
        guardar(usuario, cancha, franja, fecha, LocalTime.of(10, 0), EstadoReserva.CANCELADA);

        List<Reserva> ocupan = reservaRepository.findActiveReservationsByCanchaAndDate(cancha.getId(), fecha);

        assertThat(ocupan).extracting(Reserva::getId)
                .containsExactlyInAnyOrder(pendiente.getId(), confirmada.getId());
    }

    private Reserva guardar(Usuario usuario, Cancha cancha, FranjaHoraria franja, LocalDate fecha,
                            LocalTime inicio, EstadoReserva estado) {
        return reservaRepository.save(Reserva.builder()
                .usuario(usuario).cancha(cancha).franjaHoraria(franja)
                .fecha(fecha).horaInicio(inicio).horaFin(inicio.plusMinutes(90))
                .precioTotal(new BigDecimal("1000")).estadoReserva(estado).origen(OrigenReserva.APP)
                .build());
    }
}
