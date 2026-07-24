package com.padel.repository;

import com.padel.domain.entity.MovimientoBancoHoras;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimientoBancoHorasRepository extends JpaRepository<MovimientoBancoHoras, Long> {
    List<MovimientoBancoHoras> findByEmpleadoIdAndLiquidadoFalseAndCreatedAtBetween(Long empleadoId, LocalDateTime desde, LocalDateTime hasta);
}
