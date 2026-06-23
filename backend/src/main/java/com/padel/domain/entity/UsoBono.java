package com.padel.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "uso_bono")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsoBono extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bono_id", nullable = false)
    private Bono bono;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reserva reserva;

    @Column(name = "horas_descontadas", nullable = false)
    private Integer horasDescontadas;
}
