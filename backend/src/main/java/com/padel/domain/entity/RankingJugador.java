package com.padel.domain.entity;

import com.padel.domain.enums.CategoriaJugador;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ranking_jugadores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RankingJugador extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugador_id", nullable = false)
    private Usuario jugador;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CategoriaJugador categoria;

    @Builder.Default
    @Column(nullable = false)
    private Integer puntos = 0;

    @Builder.Default
    @Column(name = "partidos_jugados", nullable = false)
    private Integer partidosJugados = 0;

    @Builder.Default
    @Column(name = "partidos_ganados", nullable = false)
    private Integer partidosGanados = 0;
}
