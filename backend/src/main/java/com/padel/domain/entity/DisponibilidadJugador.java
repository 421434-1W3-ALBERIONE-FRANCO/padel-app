package com.padel.domain.entity;

import com.padel.domain.enums.BloqueHorario;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;

/** Un bloque horario (día de la semana + mañana/tarde/noche) en el que el jugador puede jugar. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DisponibilidadJugador {

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 10)
    private DayOfWeek diaSemana;

    @Enumerated(EnumType.STRING)
    @Column(name = "bloque", nullable = false, length = 10)
    private BloqueHorario bloque;
}
