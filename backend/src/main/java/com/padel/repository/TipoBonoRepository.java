package com.padel.repository;

import com.padel.domain.entity.TipoBono;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TipoBonoRepository extends JpaRepository<TipoBono, Long> {

    List<TipoBono> findByActivoTrueOrderByHorasAsc();

    List<TipoBono> findAllByOrderByHorasAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
