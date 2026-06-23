package com.padel.repository;

import com.padel.domain.entity.Cancha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CanchaRepository extends JpaRepository<Cancha, Long> {
    List<Cancha> findByActivaTrue();
    Optional<Cancha> findByIdAndActivaTrue(Long id);
    boolean existsByNombreAndActivaTrue(String nombre);
    boolean existsByNombreAndActivaTrueAndIdNot(String nombre, Long id);
}
