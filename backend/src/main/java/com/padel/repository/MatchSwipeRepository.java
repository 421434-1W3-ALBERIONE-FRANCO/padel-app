package com.padel.repository;

import com.padel.domain.entity.MatchSwipe;
import com.padel.domain.enums.TipoSwipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface MatchSwipeRepository extends JpaRepository<MatchSwipe, Long> {

    boolean existsByOrigenIdAndDestinoId(Long origenId, Long destinoId);

    boolean existsByOrigenIdAndDestinoIdAndTipo(Long origenId, Long destinoId, TipoSwipe tipo);

    // Jugadores a los que el usuario ya respondió (no se le vuelven a mostrar en el mazo).
    @Query("SELECT s.destino.id FROM MatchSwipe s WHERE s.origen.id = :origenId")
    Set<Long> findDestinoIdsByOrigenId(@Param("origenId") Long origenId);

    // Jugadores con "me interesa" en ambos sentidos con el usuario.
    @Query("SELECT s.origen.id FROM MatchSwipe s WHERE s.destino.id = :usuarioId AND s.tipo = 'LIKE' " +
           "AND EXISTS (SELECT 1 FROM MatchSwipe r WHERE r.origen.id = :usuarioId AND r.destino.id = s.origen.id AND r.tipo = 'LIKE')")
    List<Long> findLikesMutuos(@Param("usuarioId") Long usuarioId);
}
