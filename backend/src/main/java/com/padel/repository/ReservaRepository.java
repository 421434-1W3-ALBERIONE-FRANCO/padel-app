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

    @Query("SELECT r FROM Reserva r WHERE r.cancha.id = :canchaId AND r.fecha = :fecha AND r.estadoReserva IN ('CONFIRMADA', 'COMPLETADA')")
    List<Reserva> findActiveReservationsByCanchaAndDate(
            @Param("canchaId") Long canchaId,
            @Param("fecha") LocalDate fecha
    );

    List<Reserva> findByEstadoReservaAndCreatedAtBefore(EstadoReserva estadoReserva, LocalDateTime threshold);
}
