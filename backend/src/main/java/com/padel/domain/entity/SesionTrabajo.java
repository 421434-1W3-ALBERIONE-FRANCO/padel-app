package com.padel.domain.entity;

import com.padel.domain.enums.EstadoSesionTrabajo;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sesiones_trabajo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SesionTrabajo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Usuario empleado;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column
    private LocalDateTime fin;

    @Column(name = "minutos_trabajados")
    private Integer minutosTrabajados;

    @Builder.Default
    @Column(name = "cerrada_automaticamente", nullable = false)
    private boolean cerradaAutomaticamente = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSesionTrabajo estado;
}
