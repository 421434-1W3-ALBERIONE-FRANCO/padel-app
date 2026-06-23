package com.padel.domain.entity;

import com.padel.domain.enums.TipoSuelo;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "canchas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cancha extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_suelo", nullable = false, length = 50)
    private TipoSuelo tipoSuelo;

    @Column(nullable = false)
    private boolean techada;

    @Column(name = "tiene_luz", nullable = false)
    private boolean tieneLuz;

    @Builder.Default
    @Column(nullable = false)
    private boolean activa = true;
}
