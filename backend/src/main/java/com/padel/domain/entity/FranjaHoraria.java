package com.padel.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "franjas_horarias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FranjaHoraria extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancha_id", nullable = false)
    private Cancha cancha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "duracion_min", nullable = false)
    private Integer duracionMin;

    @Column(name = "precio_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioBase;

    @Column(name = "precio_nocturno", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioNocturno;

    @Column(name = "dias_aplicables", nullable = false)
    private String diasAplicables; // Guardado como comas: MONDAY,TUESDAY,etc.

    /** Días en que rige la franja (la columna los guarda separados por coma: MONDAY,TUESDAY,...). */
    public Set<DayOfWeek> diasAplicablesSet() {
        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        if (diasAplicables == null || diasAplicables.isBlank()) {
            return dias;
        }
        Arrays.stream(diasAplicables.split(","))
                .map(String::trim)
                .filter(d -> !d.isEmpty())
                .map(DayOfWeek::valueOf)
                .forEach(dias::add);
        return dias;
    }

    public boolean aplicaEn(DayOfWeek dia) {
        return diasAplicablesSet().contains(dia);
    }
}
