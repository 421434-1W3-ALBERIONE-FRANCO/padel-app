package com.padel.repository;

import com.padel.domain.entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findByUsuarioIdOrderByCreatedAtDesc(Long usuarioId);

    long countByUsuarioIdAndLeidaFalse(Long usuarioId);

    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true, n.leidaEn = :ahora WHERE n.usuario.id = :usuarioId AND n.leida = false")
    void marcarTodasLeidas(@Param("usuarioId") Long usuarioId, @Param("ahora") LocalDateTime ahora);
}
