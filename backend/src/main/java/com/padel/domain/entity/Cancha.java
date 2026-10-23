package com.padel.domain.entity;

import com.padel.domain.enums.TipoSuelo;
import jakarta.persistence.*;
import lombok.*;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

@Entity
@Table(name = "canchas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cancha extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_suelo", nullable = false, length = 50)
    private TipoSuelo tipoSuelo;

    @Column(nullable = false)
    private boolean techada;

    @Column(name = "tiene_luz", nullable = false)
    private boolean tieneLuz;

    @Builder.Default
    @Column(nullable = false)
    private boolean activa = true;

    // Duraciones de reserva permitidas en minutos, separadas por coma (ej. "60,90,120,180").
    // El cliente elige entre estas (según disponibilidad); default 90 (el estándar de pádel).
    @Builder.Default
    @Column(name = "duraciones_permitidas", length = 100)
    private String duracionesPermitidas = "90";

    /** Duraciones permitidas como conjunto ordenado ("60,90,120" -> {60, 90, 120}); 90 si no hay ninguna. */
    public Set<Integer> duracionesPermitidasSet() {
        Set<Integer> duraciones = new TreeSet<>();
        if (duracionesPermitidas != null) {
            Arrays.stream(duracionesPermitidas.split(","))
                    .map(String::trim)
                    .filter(d -> !d.isEmpty())
                    .map(Integer::valueOf)
                    .forEach(duraciones::add);
        }
        if (duraciones.isEmpty()) {
            duraciones.add(90);
        }
        return duraciones;
    }
}
