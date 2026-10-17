package com.padel.domain.entity;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.PosicionJuego;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/** Perfil deportivo del usuario: es lo que usa el match de parejas para proponer compañeros. */
@Entity
@Table(name = "perfiles_jugador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerfilJugador extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaJugador categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PosicionJuego posicion;

    @Column(length = 280)
    private String bio;

    @Builder.Default
    @Column(name = "visible_en_match", nullable = false)
    private boolean visibleEnMatch = true;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "disponibilidades_jugador", joinColumns = @JoinColumn(name = "perfil_id"))
    private Set<DisponibilidadJugador> disponibilidad = new HashSet<>();
}
