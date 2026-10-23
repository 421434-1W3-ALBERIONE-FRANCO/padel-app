package com.padel.repository;

import com.padel.domain.entity.PostulacionSolicitud;
import com.padel.domain.enums.EstadoPostulacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostulacionSolicitudRepository extends JpaRepository<PostulacionSolicitud, Long> {

    List<PostulacionSolicitud> findBySolicitudId(Long solicitudId);

    List<PostulacionSolicitud> findBySolicitudIdAndEstado(Long solicitudId, EstadoPostulacion estado);

    Optional<PostulacionSolicitud> findByIdAndSolicitudId(Long id, Long solicitudId);

    boolean existsBySolicitudIdAndJugadorId(Long solicitudId, Long jugadorId);

    List<PostulacionSolicitud> findByJugadorIdOrderByCreatedAtDesc(Long jugadorId);

    // Postulaciones de varias solicitudes en una sola consulta (antes era una consulta por solicitud).
    @Query("SELECT p FROM PostulacionSolicitud p JOIN FETCH p.jugador WHERE p.solicitud.id IN :ids ORDER BY p.createdAt, p.id")
    List<PostulacionSolicitud> findConJugadorBySolicitudIdIn(@Param("ids") Collection<Long> ids);
}
