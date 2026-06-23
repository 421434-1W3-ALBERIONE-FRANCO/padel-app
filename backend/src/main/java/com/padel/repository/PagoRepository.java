package com.padel.repository;

import com.padel.domain.entity.Pago;
import com.padel.domain.enums.EstadoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByMpPaymentId(String mpPaymentId);
    Optional<Pago> findByMpPreferenceId(String mpPreferenceId);
    List<Pago> findByReservaId(Long reservaId);
    Optional<Pago> findByReservaIdAndEstado(Long reservaId, EstadoPago estado);
}
