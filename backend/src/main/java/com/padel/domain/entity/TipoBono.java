package com.padel.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Catálogo de bonos que vende el club (horas, precio y vigencia), administrado por el ADMIN. Antes los packs
 * estaban fijos en el frontend y quien vendía podía cambiar horas y precio a mano: un bono con más horas, a
 * $0 o con el precio inflado para inflar su comisión.
 */
@Entity
@Table(name = "tipos_bono")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipoBono extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    private Integer horas;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(name = "dias_vigencia", nullable = false)
    private Integer diasVigencia;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;
}
