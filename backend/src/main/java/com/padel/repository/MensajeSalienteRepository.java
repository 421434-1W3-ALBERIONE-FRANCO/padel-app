package com.padel.repository;

import com.padel.domain.entity.MensajeSaliente;
import com.padel.domain.enums.EstadoMensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MensajeSalienteRepository extends JpaRepository<MensajeSaliente, Long> {

    List<MensajeSaliente> findTop50ByEstadoAndProximoIntentoEnLessThanEqualOrderByIdAsc(EstadoMensaje estado, LocalDateTime ahora);
}
