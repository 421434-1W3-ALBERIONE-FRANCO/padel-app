package com.padel.repository;

import com.padel.domain.entity.BloqueoCancha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface BloqueoCanchaRepository extends JpaRepository<BloqueoCancha, Long> {

    List<BloqueoCancha> findByCanchaId(Long canchaId);

    @Query("SELECT b FROM BloqueoCancha b WHERE b.cancha.id = :canchaId " +
           "AND b.fechaDesde <= :fechaHasta AND b.fechaHasta >= :fechaDesde " +
           "AND b.horaDesde < :horaHasta AND b.horaHasta > :horaDesde " +
           "AND (:id IS NULL OR b.id <> :id)")
    List<BloqueoCancha> findOverlappingBlocks(
            @Param("canchaId") Long canchaId,
            @Param("fechaDesde") LocalDate fechaDesde,
            @Param("fechaHasta") LocalDate fechaHasta,
            @Param("horaDesde") LocalTime horaDesde,
            @Param("horaHasta") LocalTime horaHasta,
            @Param("id") Long id
    );

    @Query("SELECT b FROM BloqueoCancha b WHERE b.cancha.id = :canchaId " +
           "AND b.fechaDesde <= :fecha AND b.fechaHasta >= :fecha")
    List<BloqueoCancha> findBlocksForDate(
            @Param("canchaId") Long canchaId,
            @Param("fecha") LocalDate fecha
    );
}
