package com.padel.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "liquidaciones_sueldo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiquidacionSueldo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Usuario empleado;

    @Column(name = "periodo_desde", nullable = false)
    private LocalDate periodoDesde;

    @Column(name = "periodo_hasta", nullable = false)
    private LocalDate periodoHasta;

    @Column(name = "total_minutos_trabajados", nullable = false)
    private Integer totalMinutosTrabajados;

    @Column(name = "total_minutos_banco_horas", nullable = false)
    private Integer totalMinutosBancoHoras;

    @Column(name = "total_comisiones", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalComisiones;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generada_por_id", nullable = false)
    private Usuario generadaPor;

    @Column(name = "generada_en", nullable = false)
    private LocalDateTime generadaEn;
}
