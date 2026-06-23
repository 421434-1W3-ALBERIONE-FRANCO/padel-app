package com.padel.repository;

import com.padel.domain.entity.Consumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsumoRepository extends JpaRepository<Consumo, Long> {
    List<Consumo> findByReservaId(Long reservaId);
    List<Consumo> findByPagoId(Long pagoId);
}
