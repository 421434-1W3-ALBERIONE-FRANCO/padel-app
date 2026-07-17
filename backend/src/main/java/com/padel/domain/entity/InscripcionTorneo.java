package com.padel.domain.entity;

import com.padel.domain.enums.EstadoInscripcion;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inscripciones_torneo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InscripcionTorneo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id", nullable = false)
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugador1_id", nullable = false)
    private Usuario jugador1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugador2_id", nullable = false)
    private Usuario jugador2;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 50)
    private EstadoInscripcion estado = EstadoInscripcion.CONFIRMADA;
}
