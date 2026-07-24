package com.padel.repository;

import com.padel.domain.entity.LiquidacionSueldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LiquidacionSueldoRepository extends JpaRepository<LiquidacionSueldo, Long> {
    List<LiquidacionSueldo> findByEmpleadoIdOrderByGeneradaEnDesc(Long empleadoId);
}
