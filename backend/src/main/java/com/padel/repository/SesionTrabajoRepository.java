package com.padel.repository;

import com.padel.domain.entity.SesionTrabajo;
import com.padel.domain.enums.EstadoSesionTrabajo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SesionTrabajoRepository extends JpaRepository<SesionTrabajo, Long> {
    Optional<SesionTrabajo> findFirstByEmpleadoIdAndEstado(Long empleadoId, EstadoSesionTrabajo estado);
    List<SesionTrabajo> findByEstado(EstadoSesionTrabajo estado);
    List<SesionTrabajo> findByEmpleadoIdAndEstadoAndInicioBetween(Long empleadoId, EstadoSesionTrabajo estado, LocalDateTime desde, LocalDateTime hasta);
}
