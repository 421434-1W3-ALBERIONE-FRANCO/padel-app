package com.padel.repository;

import com.padel.domain.entity.Pago;
import com.padel.domain.enums.ConceptoPago;
import com.padel.domain.enums.EstadoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByMpPaymentId(String mpPaymentId);
    Optional<Pago> findByMpPreferenceId(String mpPreferenceId);
    List<Pago> findByReservaId(Long reservaId);
    // Con el concepto: la reserva puede tener además el pago de sus consumos (y con Point, pendiente).
    Optional<Pago> findByReservaIdAndEstadoAndConcepto(Long reservaId, EstadoPago estado, ConceptoPago concepto);
    boolean existsByReservaIdAndEstadoAndConcepto(Long reservaId, EstadoPago estado, ConceptoPago concepto);

    // Movimientos de la caja: trae en la misma consulta lo que muestra cada línea (cliente, staff, cancha, bono).
    @Query("SELECT p FROM Pago p JOIN FETCH p.usuario LEFT JOIN FETCH p.atendidoPor LEFT JOIN FETCH p.reserva r " +
           "LEFT JOIN FETCH r.cancha LEFT JOIN FETCH p.bono " +
           "WHERE p.estado = :estado AND p.createdAt BETWEEN :desde AND :hasta ORDER BY p.createdAt, p.id")
    List<Pago> findMovimientosDeCaja(@Param("estado") EstadoPago estado,
                                     @Param("desde") LocalDateTime desde,
                                     @Param("hasta") LocalDateTime hasta);
}
