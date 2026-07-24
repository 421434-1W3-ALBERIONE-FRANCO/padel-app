package com.padel.repository;

import com.padel.domain.entity.BancoHoras;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BancoHorasRepository extends JpaRepository<BancoHoras, Long> {
    Optional<BancoHoras> findByEmpleadoId(Long empleadoId);
}
