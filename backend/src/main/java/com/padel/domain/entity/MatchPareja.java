package com.padel.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Match mutuo entre dos jugadores (ambos se dieron "me interesa"). Se guarda con jugador1 = el de
 * menor id para que la restricción única de la pareja no dependa de quién respondió último.
 */
@Entity
@Table(name = "match_parejas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchPareja extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugador1_id", nullable = false)
    private Usuario jugador1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugador2_id", nullable = false)
    private Usuario jugador2;

    public boolean incluye(Long usuarioId) {
        return jugador1.getId().equals(usuarioId) || jugador2.getId().equals(usuarioId);
    }

    public Usuario companeroDe(Long usuarioId) {
        return jugador1.getId().equals(usuarioId) ? jugador2 : jugador1;
    }
}
