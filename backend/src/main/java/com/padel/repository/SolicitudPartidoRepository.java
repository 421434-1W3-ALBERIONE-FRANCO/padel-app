package com.padel.repository;

import com.padel.domain.entity.SolicitudPartido;
import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoSolicitud;
import com.padel.domain.enums.TipoSolicitud;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SolicitudPartidoRepository extends JpaRepository<SolicitudPartido, Long> {

    @Query("SELECT s FROM SolicitudPartido s WHERE " +
            "(:estado IS NULL OR s.estado = :estado) AND " +
            "(:categoria IS NULL OR s.categoria = :categoria) AND " +
            "(:tipoSolicitud IS NULL OR s.tipoSolicitud = :tipoSolicitud) " +
            "ORDER BY s.fechaHoraPropuesta ASC")
    List<SolicitudPartido> buscar(EstadoSolicitud estado, CategoriaJugador categoria, TipoSolicitud tipoSolicitud);

    List<SolicitudPartido> findByCreadorEmailOrderByCreatedAtDesc(String email);
}
