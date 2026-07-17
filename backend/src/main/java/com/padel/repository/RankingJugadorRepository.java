package com.padel.repository;

import com.padel.domain.entity.RankingJugador;
import com.padel.domain.enums.CategoriaJugador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RankingJugadorRepository extends JpaRepository<RankingJugador, Long> {

    List<RankingJugador> findByCategoriaOrderByPuntosDescPartidosGanadosDesc(CategoriaJugador categoria);

    Optional<RankingJugador> findByJugadorIdAndCategoria(Long jugadorId, CategoriaJugador categoria);
}
