package com.padel.domain.entity;

import com.padel.domain.enums.EstadoPartido;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "partidos_torneo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartidoTorneo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id", nullable = false)
    private Torneo torneo;

    @Column(nullable = false, length = 100)
    private String ronda;

    @Column(name = "numero_ronda", nullable = false)
    private Integer numeroRonda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inscripcion1_id", nullable = false)
    private InscripcionTorneo inscripcion1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inscripcion2_id")
    private InscripcionTorneo inscripcion2;

    @Column(name = "fecha_hora")
    private LocalDateTime fechaHora;

    @Column(name = "sets_pareja1")
    private Integer setsPareja1;

    @Column(name = "sets_pareja2")
    private Integer setsPareja2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ganador_inscripcion_id")
    private InscripcionTorneo ganador;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 50)
    private EstadoPartido estado = EstadoPartido.PENDIENTE;
}
