package com.padel.domain.entity;

import com.padel.domain.enums.TipoSwipe;
import jakarta.persistence.*;
import lombok.*;

/** Respuesta de un jugador (origen) a la tarjeta de otro (destino): me interesa o paso. */
@Entity
@Table(name = "match_swipes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchSwipe extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origen_id", nullable = false)
    private Usuario origen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destino_id", nullable = false)
    private Usuario destino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoSwipe tipo;
}
