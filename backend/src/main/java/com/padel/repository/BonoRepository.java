package com.padel.repository;

import com.padel.domain.entity.Bono;
import com.padel.domain.enums.EstadoBono;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BonoRepository extends JpaRepository<Bono, Long> {
    List<Bono> findByUsuarioIdAndEstadoOrderByFechaVencimientoAsc(Long usuarioId, EstadoBono estado);
    List<Bono> findByUsuarioIdOrderByFechaVencimientoDesc(Long usuarioId);
    List<Bono> findByEstadoAndFechaVencimientoBefore(EstadoBono estado, LocalDate date);

    List<Bono> findByEstadoAndFechaVencimientoBetweenAndAvisoVencimientoEnviadoFalse(
            EstadoBono estado, LocalDate desde, LocalDate hasta);

    // Bloquea los bonos activos del jugador mientras se descuentan horas: dos pagos con bono en paralelo
    // leían el mismo saldo y descontaban las mismas horas dos veces.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bono b WHERE b.usuario.id = :usuarioId AND b.estado = 'ACTIVO' ORDER BY b.fechaVencimiento ASC, b.id ASC")
    List<Bono> findActivosParaUsar(@Param("usuarioId") Long usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bono b WHERE b.id = :id")
    Optional<Bono> findByIdForUpdate(@Param("id") Long id);
}
