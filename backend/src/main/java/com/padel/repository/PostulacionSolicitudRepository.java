package com.padel.repository;

import com.padel.domain.entity.PostulacionSolicitud;
import com.padel.domain.enums.EstadoPostulacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostulacionSolicitudRepository extends JpaRepository<PostulacionSolicitud, Long> {

    List<PostulacionSolicitud> findBySolicitudId(Long solicitudId);

    List<PostulacionSolicitud> findBySolicitudIdAndEstado(Long solicitudId, EstadoPostulacion estado);

    Optional<PostulacionSolicitud> findByIdAndSolicitudId(Long id, Long solicitudId);

    boolean existsBySolicitudIdAndJugadorId(Long solicitudId, Long jugadorId);

    List<PostulacionSolicitud> findByJugadorEmailOrderByCreatedAtDesc(String email);
}
