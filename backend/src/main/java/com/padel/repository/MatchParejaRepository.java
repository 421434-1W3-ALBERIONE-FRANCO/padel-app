package com.padel.repository;

import com.padel.domain.entity.MatchPareja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchParejaRepository extends JpaRepository<MatchPareja, Long> {

    Optional<MatchPareja> findByJugador1IdAndJugador2Id(Long jugador1Id, Long jugador2Id);

    @Query("SELECT m FROM MatchPareja m JOIN FETCH m.jugador1 JOIN FETCH m.jugador2 " +
           "WHERE m.jugador1.id = :usuarioId OR m.jugador2.id = :usuarioId ORDER BY m.createdAt DESC")
    List<MatchPareja> findDelUsuario(@Param("usuarioId") Long usuarioId);
}
