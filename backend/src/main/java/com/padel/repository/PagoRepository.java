package com.padel.repository;

import com.padel.domain.entity.Pago;
import com.padel.domain.enums.ConceptoPago;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.MetodoPago;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pago p WHERE p.id = :id")
    Optional<Pago> findByIdForUpdate(@Param("id") Long id);

    // Con el concepto: la reserva puede tener además el pago de sus consumos (y con Point, pendiente).
    Optional<Pago> findByReservaIdAndEstadoAndConcepto(Long reservaId, EstadoPago estado, ConceptoPago concepto);

    List<Pago> findAllByReservaIdAndEstadoAndConcepto(Long reservaId, EstadoPago estado, ConceptoPago concepto);

    boolean existsByReservaIdAndEstadoAndConcepto(Long reservaId, EstadoPago estado, ConceptoPago concepto);

    boolean existsByPagoOriginalId(Long pagoOriginalId);

    // Reintegros que el staff tiene que entregar (o que MercadoPago no pudo devolver solo).
    @Query("SELECT p FROM Pago p JOIN FETCH p.usuario LEFT JOIN FETCH p.reserva r LEFT JOIN FETCH r.cancha " +
           "WHERE p.concepto = 'REINTEGRO' AND p.estado = 'PENDIENTE' ORDER BY p.createdAt, p.id")
    List<Pago> findReintegrosPendientes();

    // Reintegros por MercadoPago que todavía se pueden intentar en forma automática.
    List<Pago> findTop20ByConceptoAndEstadoAndMetodoAndIntentosReintegroLessThanOrderByIdAsc(
            ConceptoPago concepto, EstadoPago estado, MetodoPago metodo, Integer intentos);

    // Movimientos de la caja: trae en la misma consulta lo que muestra cada línea (cliente, staff, cancha, bono).
    // Se agrupa por el momento en que la plata se movió (aprobadoEn), no por la creación del registro.
    @Query("SELECT p FROM Pago p JOIN FETCH p.usuario LEFT JOIN FETCH p.atendidoPor LEFT JOIN FETCH p.reserva r " +
           "LEFT JOIN FETCH r.cancha LEFT JOIN FETCH p.bono " +
           "WHERE p.estado = :estado AND p.aprobadoEn BETWEEN :desde AND :hasta ORDER BY p.aprobadoEn, p.id")
    List<Pago> findMovimientosDeCaja(@Param("estado") EstadoPago estado,
                                     @Param("desde") LocalDateTime desde,
                                     @Param("hasta") LocalDateTime hasta);
}
