package com.padel.repository;

import com.padel.domain.entity.TurnoEmpleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface TurnoEmpleadoRepository extends JpaRepository<TurnoEmpleado, Long> {
    List<TurnoEmpleado> findByEmpleadoId(Long empleadoId);
    Optional<TurnoEmpleado> findByEmpleadoIdAndDiaSemana(Long empleadoId, DayOfWeek diaSemana);
}
