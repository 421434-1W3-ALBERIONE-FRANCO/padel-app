package com.padel.repository;

import com.padel.domain.entity.Reserva;
import com.padel.domain.enums.EstadoReserva;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long>, JpaSpecificationExecutor<Reserva> {

    List<Reserva> findByUsuarioIdOrderByFechaDesc(Long usuarioId);

    // Bloquea la fila de la reserva hasta el fin de la transacción: cobrar, cerrar la cuenta o usar un bono
    // dos veces en paralelo (doble click) dejaba dos pagos para el mismo turno.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reserva r WHERE r.id = :id")
    Optional<Reserva> findByIdForUpdate(@Param("id") Long id);

    long countByUsuarioIdAndEstadoReserva(Long usuarioId, EstadoReserva estadoReserva);

    boolean existsByCanchaIdAndFechaGreaterThanEqualAndEstadoReservaIn(Long canchaId, LocalDate fecha, Collection<EstadoReserva> estados);

    // Reservas que ocupan la cancha ese día. Incluye PENDIENTE_PAGO: el turno queda retenido mientras
    // se paga (hasta que el scheduler lo cancela por falta de pago).
    @Query("SELECT r FROM Reserva r WHERE r.cancha.id = :canchaId AND r.fecha = :fecha AND r.estadoReserva IN ('PENDIENTE_PAGO', 'CONFIRMADA', 'COMPLETADA')")
    List<Reserva> findActiveReservationsByCanchaAndDate(
            @Param("canchaId") Long canchaId,
            @Param("fecha") LocalDate fecha
    );

    // Reservas vigentes (pendientes o confirmadas) de una cancha en un rango de fechas, para avisar o cancelar
    // las que quedan dentro de un bloqueo nuevo.
    @Query("SELECT r FROM Reserva r JOIN FETCH r.usuario WHERE r.cancha.id = :canchaId AND r.fecha BETWEEN :desde AND :hasta " +
           "AND r.estadoReserva IN ('PENDIENTE_PAGO', 'CONFIRMADA') ORDER BY r.fecha, r.horaInicio")
    List<Reserva> findVigentesEntreFechas(@Param("canchaId") Long canchaId,
                                          @Param("desde") LocalDate desde,
                                          @Param("hasta") LocalDate hasta);

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

    // Métricas del panel de administración: cantidad e importe por día y estado, agregados en la base.
    @Query("SELECT r.fecha, r.estadoReserva, COUNT(r), COALESCE(SUM(r.precioTotal), 0) FROM Reserva r " +
           "WHERE r.fecha BETWEEN :desde AND :hasta GROUP BY r.fecha, r.estadoReserva")
    List<Object[]> resumirPorFechaYEstado(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
