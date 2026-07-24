package com.padel.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "configuracion_sistema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionSistema extends BaseEntity {

    // Fila única (id=1) sembrada por migración: no se generan nuevas filas, solo se actualiza.
    @Id
    private Long id;

    @Column(name = "comisiones_habilitadas", nullable = false)
    private boolean comisionesHabilitadas;

    @Column(name = "porcentaje_comision_recepcion", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeComisionRecepcion;

    @Column(name = "dias_aviso_vencimiento_bono", nullable = false)
    private Integer diasAvisoVencimientoBono;

    @Column(name = "hora_tope_cierre_sesion", nullable = false)
    private LocalTime horaTopeCierreSesion;
}
