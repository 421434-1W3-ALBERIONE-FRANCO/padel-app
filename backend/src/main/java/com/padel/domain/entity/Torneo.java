package com.padel.domain.entity;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoTorneo;
import com.padel.domain.enums.FormatoTorneo;
import com.padel.domain.enums.TipoTorneo;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "torneos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Torneo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TipoTorneo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FormatoTorneo formato;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CategoriaJugador categoria;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "max_parejas", nullable = false)
    private Integer maxParejas;

    @Column(name = "precio_inscripcion", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioInscripcion;

    @Column(length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 50)
    private EstadoTorneo estado = EstadoTorneo.INSCRIPCION_ABIERTA;
}
