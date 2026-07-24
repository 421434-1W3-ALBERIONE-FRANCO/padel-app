package com.padel.repository;

import com.padel.domain.entity.Comision;
import com.padel.domain.enums.EstadoComision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ComisionRepository extends JpaRepository<Comision, Long> {
    List<Comision> findByRecepcionistaIdAndCreatedAtBetween(Long recepcionistaId, LocalDateTime desde, LocalDateTime hasta);
    List<Comision> findByRecepcionistaIdAndEstadoAndCreatedAtBetween(Long recepcionistaId, EstadoComision estado, LocalDateTime desde, LocalDateTime hasta);
}
