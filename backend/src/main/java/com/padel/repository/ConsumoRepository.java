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
    // Fila pendiente y todavía sin pago (las que ya se mandaron a cobrar al Point no se tocan).
    Optional<Consumo> findFirstByReserva_IdAndProducto_IdAndEstadoPagoAndPagoIsNull(Long reservaId, Long productoId, EstadoConsumoPago estadoPago);
}
