package com.padel.repository;

import com.padel.domain.entity.PerfilJugador;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PerfilJugadorRepository extends JpaRepository<PerfilJugador, Long> {

    @EntityGraph(attributePaths = {"usuario", "disponibilidad"})
    Optional<PerfilJugador> findByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = {"usuario", "disponibilidad"})
    List<PerfilJugador> findByUsuarioIdIn(Collection<Long> usuarioIds);

    // Perfiles que se muestran en el mazo, con usuario y disponibilidad en una sola consulta.
    @EntityGraph(attributePaths = {"usuario", "disponibilidad"})
    List<PerfilJugador> findByVisibleEnMatchTrueAndUsuarioActivoTrue();
}
