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
    // El nombre es UNIQUE en la tabla para todas las canchas (también las dadas de baja), así que
    // la validación no puede limitarse a las activas: si no, la base rechaza el INSERT con un 500.
    boolean existsByNombre(String nombre);
    boolean existsByNombreAndIdNot(String nombre, Long id);
}
