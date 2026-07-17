package com.padel.domain.entity;

import com.padel.domain.enums.EstadoPostulacion;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "postulaciones_solicitud")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostulacionSolicitud extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_id", nullable = false)
    private SolicitudPartido solicitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugador_id", nullable = false)
    private Usuario jugador;

    @Column(length = 300)
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 50)
    private EstadoPostulacion estado = EstadoPostulacion.PENDIENTE;
}
