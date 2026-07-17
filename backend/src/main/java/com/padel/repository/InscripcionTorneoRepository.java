package com.padel.repository;

import com.padel.domain.entity.InscripcionTorneo;
import com.padel.domain.enums.EstadoInscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InscripcionTorneoRepository extends JpaRepository<InscripcionTorneo, Long> {

    List<InscripcionTorneo> findByTorneoIdAndEstado(Long torneoId, EstadoInscripcion estado);

    List<InscripcionTorneo> findByTorneoId(Long torneoId);

    long countByTorneoIdAndEstado(Long torneoId, EstadoInscripcion estado);

    Optional<InscripcionTorneo> findByIdAndTorneoId(Long id, Long torneoId);
}
