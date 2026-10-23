package com.padel.repository;

import com.padel.domain.entity.InscripcionTorneo;
import com.padel.domain.enums.EstadoInscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InscripcionTorneoRepository extends JpaRepository<InscripcionTorneo, Long> {

    List<InscripcionTorneo> findByTorneoIdAndEstado(Long torneoId, EstadoInscripcion estado);

    List<InscripcionTorneo> findByTorneoId(Long torneoId);

    long countByTorneoIdAndEstado(Long torneoId, EstadoInscripcion estado);

    Optional<InscripcionTorneo> findByIdAndTorneoId(Long id, Long torneoId);

    List<InscripcionTorneo> findByTorneoIdAndEstadoIn(Long torneoId, Collection<EstadoInscripcion> estados);

    // Parejas confirmadas de varios torneos en una sola consulta (antes era un COUNT por torneo listado).
    @Query("SELECT i.torneo.id, COUNT(i) FROM InscripcionTorneo i WHERE i.torneo.id IN :ids AND i.estado = 'CONFIRMADA' GROUP BY i.torneo.id")
    List<Object[]> contarConfirmadasPorTorneo(@Param("ids") Collection<Long> ids);
}
