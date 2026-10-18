package com.padel.repository;

import com.padel.domain.entity.Reserva;
import com.padel.domain.enums.EstadoReserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    // Reservas de un día (todos los estados) para las vistas de staff que refrescan seguido: ocupación en vivo
    // y carga de consumos. Ordenadas como se juegan. Trae usuario y cancha en la misma consulta porque el mapper
    // los lee y, al ser LAZY, cada usuario y cancha distintos costaban una consulta más en cada refresco.
    @Query("SELECT r FROM Reserva r JOIN FETCH r.usuario JOIN FETCH r.cancha c WHERE r.fecha = :fecha ORDER BY r.horaInicio, c.nombre, r.id")
    List<Reserva> findDelDia(@Param("fecha") LocalDate fecha);

    List<Reserva> findByEstadoReservaAndCreatedAtBefore(EstadoReserva estadoReserva, LocalDateTime threshold);

    // Reservas CONFIRMADAS cuyo turno ya terminó (fecha + horaFin <= ahora): las de días anteriores y las de hoy
    // ya finalizadas. Un turno empieza y termina el mismo día (las franjas no cruzan la medianoche), así que
    // alcanza con comparar fecha y hora por separado.
    @Query("SELECT r FROM Reserva r WHERE r.estadoReserva = 'CONFIRMADA' AND (r.fecha < :hoy OR (r.fecha = :hoy AND r.horaFin <= :hora))")
    List<Reserva> findConfirmadasFinalizadas(@Param("hoy") LocalDate hoy, @Param("hora") LocalTime hora);

    List<Reserva> findByFechaAndEstadoReservaAndRecordatorioEnviadoFalse(LocalDate fecha, EstadoReserva estadoReserva);

    // Turnos del día confirmados (o ya jugados) sin un pago aprobado del turno: los que recepción confirmó para
    // cobrar al llegar. La caja diaria los lista como pendientes de cobro. El pago de los consumos no cuenta.
    @Query("SELECT r FROM Reserva r JOIN FETCH r.usuario JOIN FETCH r.cancha c " +
           "WHERE r.fecha = :fecha AND r.estadoReserva IN ('CONFIRMADA', 'COMPLETADA') AND r.precioTotal > 0 " +
           "AND NOT EXISTS (SELECT p.id FROM Pago p WHERE p.reserva = r AND p.estado = 'APROBADO' AND p.concepto = 'TURNO') " +
           "ORDER BY r.horaInicio, c.nombre, r.id")
    List<Reserva> findSinCobroDelDia(@Param("fecha") LocalDate fecha);
}
