package com.padel.repository;

import com.padel.domain.entity.Torneo;
import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoTorneo;
import com.padel.domain.enums.TipoTorneo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TorneoRepository extends JpaRepository<Torneo, Long> {

    @Query("SELECT t FROM Torneo t WHERE " +
            "(:estado IS NULL OR t.estado = :estado) AND " +
            "(:categoria IS NULL OR t.categoria = :categoria) AND " +
            "(:tipo IS NULL OR t.tipo = :tipo) " +
            "ORDER BY t.fechaInicio DESC")
    List<Torneo> buscar(EstadoTorneo estado, CategoriaJugador categoria, TipoTorneo tipo);

    // Serializa las aceptaciones de invitaciones de un torneo: dos aceptaciones simultáneas podían pasar el cupo.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Torneo t WHERE t.id = :id")
    Optional<Torneo> findByIdForUpdate(@Param("id") Long id);
}
