package com.padel.repository;

import com.padel.domain.entity.Reserva;
import com.padel.domain.enums.EstadoReserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    List<Reserva> findByUsuarioIdOrderByFechaDesc(Long usuarioId);

    // Reservas que ocupan la cancha ese día. Incluye PENDIENTE_PAGO: el turno queda retenido mientras
    // se paga (hasta que el scheduler lo cancela por falta de pago). Antes solo se miraba el lock de Redis
    // de la hora de inicio, que vence a los 10 min y no cubre las franjas siguientes de una reserva larga:
    // otro usuario podía reservar encima y, si ambos pagaban, la cancha quedaba vendida dos veces.
    @Query("SELECT r FROM Reserva r WHERE r.cancha.id = :canchaId AND r.fecha = :fecha AND r.estadoReserva IN ('PENDIENTE_PAGO', 'CONFIRMADA', 'COMPLETADA')")
    List<Reserva> findActiveReservationsByCanchaAndDate(
            @Param("canchaId") Long canchaId,
            @Param("fecha") LocalDate fecha
    );

    List<Reserva> findByEstadoReservaAndCreatedAtBefore(EstadoReserva estadoReserva, LocalDateTime threshold);

    List<Reserva> findByFechaAndEstadoReservaAndRecordatorioEnviadoFalse(LocalDate fecha, EstadoReserva estadoReserva);
}
