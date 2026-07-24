package com.padel.domain.entity;

import com.padel.domain.enums.EstadoBono;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "bonos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bono extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "horas_totales", nullable = false)
    private Integer horasTotales;

    @Column(name = "horas_usadas", nullable = false)
    @Builder.Default
    private Integer horasUsadas = 0;

    @Column(name = "precio_pagado", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioPagado;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoBono estado;

    @Builder.Default
    @Column(name = "aviso_vencimiento_enviado", nullable = false)
    private boolean avisoVencimientoEnviado = false;

    // Staff (RECEPCIONISTA/ADMIN) que vendió/asignó el bono; base para el cálculo de comisiones.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendido_por_id")
    private Usuario vendidoPor;
}
