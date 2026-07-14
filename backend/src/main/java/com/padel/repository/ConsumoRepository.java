package com.padel.repository;

import com.padel.domain.entity.Consumo;
import com.padel.domain.enums.EstadoConsumoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsumoRepository extends JpaRepository<Consumo, Long> {
    List<Consumo> findByReservaId(Long reservaId);
    List<Consumo> findByPagoId(Long pagoId);
    Optional<Consumo> findByReserva_IdAndProducto_IdAndEstadoPago(Long reservaId, Long productoId, EstadoConsumoPago estadoPago);
}
