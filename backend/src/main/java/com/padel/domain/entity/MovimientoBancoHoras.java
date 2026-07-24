package com.padel.domain.entity;

import com.padel.domain.enums.TipoMovimientoBancoHoras;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "movimientos_banco_horas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoBancoHoras extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Usuario empleado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sesion_trabajo_id")
    private SesionTrabajo sesionTrabajo;

    // Minutos con signo: positivo acredita (apertura temprana/cierre tardío), negativo al liquidar.
    @Column(nullable = false)
    private Integer minutos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimientoBancoHoras tipo;

    @Builder.Default
    @Column(nullable = false)
    private boolean liquidado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "liquidacion_id")
    private LiquidacionSueldo liquidacion;
}
