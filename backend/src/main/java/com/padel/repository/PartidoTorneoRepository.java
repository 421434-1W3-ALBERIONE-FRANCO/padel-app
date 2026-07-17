package com.padel.repository;

import com.padel.domain.entity.PartidoTorneo;
import com.padel.domain.enums.EstadoPartido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartidoTorneoRepository extends JpaRepository<PartidoTorneo, Long> {

    List<PartidoTorneo> findByTorneoIdOrderByNumeroRondaAscIdAsc(Long torneoId);

    List<PartidoTorneo> findByTorneoIdAndNumeroRonda(Long torneoId, Integer numeroRonda);

    Optional<PartidoTorneo> findByIdAndTorneoId(Long id, Long torneoId);

    boolean existsByTorneoId(Long torneoId);

    long countByTorneoIdAndNumeroRondaAndEstado(Long torneoId, Integer numeroRonda, EstadoPartido estado);

    long countByTorneoIdAndNumeroRonda(Long torneoId, Integer numeroRonda);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(p.numeroRonda) FROM PartidoTorneo p WHERE p.torneo.id = :torneoId")
    Integer obtenerUltimaRonda(Long torneoId);
}
