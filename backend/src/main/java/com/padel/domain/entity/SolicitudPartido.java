package com.padel.domain.entity;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoSolicitud;
import com.padel.domain.enums.TipoSolicitud;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "solicitudes_partido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudPartido extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creador_id", nullable = false)
    private Usuario creador;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_solicitud", nullable = false, length = 50)
    private TipoSolicitud tipoSolicitud;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CategoriaJugador categoria;

    @Column(name = "cantidad_jugadores_faltantes", nullable = false)
    private Integer cantidadJugadoresFaltantes;

    @Column(name = "fecha_hora_propuesta", nullable = false)
    private LocalDateTime fechaHoraPropuesta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancha_id")
    private Cancha cancha;

    @Column(length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 50)
    private EstadoSolicitud estado = EstadoSolicitud.ABIERTA;
}
