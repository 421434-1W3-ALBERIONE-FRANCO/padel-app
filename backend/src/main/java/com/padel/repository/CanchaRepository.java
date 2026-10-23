package com.padel.repository;

import com.padel.domain.entity.Cancha;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Serializa las altas de reservas sobre una misma cancha: el control de superposición deja de ser
    // "leer y después insertar" con una ventana en la que dos pedidos simultáneos veían el turno libre.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cancha c WHERE c.id = :id")
    Optional<Cancha> findByIdForUpdate(@Param("id") Long id);
}
