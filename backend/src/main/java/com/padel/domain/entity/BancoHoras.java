package com.padel.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "banco_horas", uniqueConstraints = {
        @UniqueConstraint(name = "uk_banco_horas_empleado", columnNames = {"empleado_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BancoHoras extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Usuario empleado;

    @Builder.Default
    @Column(name = "saldo_minutos", nullable = false)
    private Integer saldoMinutos = 0;
}
