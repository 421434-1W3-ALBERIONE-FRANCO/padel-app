package com.padel.repository;

import com.padel.domain.entity.Bono;
import com.padel.domain.enums.EstadoBono;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BonoRepository extends JpaRepository<Bono, Long> {
    List<Bono> findByUsuarioIdAndEstadoOrderByFechaVencimientoAsc(Long usuarioId, EstadoBono estado);
    List<Bono> findByUsuarioIdOrderByFechaVencimientoDesc(Long usuarioId);
    List<Bono> findByEstadoAndFechaVencimientoBefore(EstadoBono estado, LocalDate date);
}
