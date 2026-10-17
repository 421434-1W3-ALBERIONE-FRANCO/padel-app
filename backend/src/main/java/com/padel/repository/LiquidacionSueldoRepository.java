package com.padel.repository;

import com.padel.domain.entity.LiquidacionSueldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LiquidacionSueldoRepository extends JpaRepository<LiquidacionSueldo, Long> {
    List<LiquidacionSueldo> findByEmpleadoIdOrderByGeneradaEnDesc(Long empleadoId);

    // Hay solapamiento si una liquidación existente empieza antes del fin del período nuevo y termina
    // después de su inicio.
    boolean existsByEmpleadoIdAndPeriodoDesdeLessThanEqualAndPeriodoHastaGreaterThanEqual(
            Long empleadoId, LocalDate hasta, LocalDate desde);
}
