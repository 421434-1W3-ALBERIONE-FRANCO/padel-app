package com.padel.domain.entity;

import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.OrigenReserva;
import com.padel.domain.valueobject.RangoHorario;
import com.padel.exception.ReservaNoModificableException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "reservas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reserva extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Bloqueo optimista: si el webhook de pago y el scheduler de vencimiento tocan la misma reserva a la
    // vez, el segundo falla en lugar de pisar al primero (una reserva pagada podía quedar CANCELADA).
    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancha_id", nullable = false)
    private Cancha cancha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "franja_id", nullable = false)
    private FranjaHoraria franjaHoraria;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "precio_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioTotal;

    // Sin setter: el estado solo cambia por las transiciones de abajo (confirmar, cancelar, completar).
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_reserva", nullable = false)
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private EstadoReserva estadoReserva = EstadoReserva.PENDIENTE_PAGO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigenReserva origen;

    @Column(name = "motivo_cancelacion")
    @Setter(AccessLevel.NONE)
    private String motivoCancelacion;

    @Builder.Default
    @Column(name = "recordatorio_enviado", nullable = false)
    private boolean recordatorioEnviado = false;

    public void confirmar() {
        cambiarEstado(EstadoReserva.CONFIRMADA);
    }

    public void cancelar(String motivo) {
        cambiarEstado(EstadoReserva.CANCELADA);
        this.motivoCancelacion = motivo;
    }

    public void completar() {
        cambiarEstado(EstadoReserva.COMPLETADA);
    }

    public RangoHorario rango() {
        return new RangoHorario(horaInicio, horaFin);
    }

    public LocalDateTime inicio() {
        return LocalDateTime.of(fecha, horaInicio);
    }

    private void cambiarEstado(EstadoReserva destino) {
        if (!estadoReserva.puedePasarA(destino)) {
            throw new ReservaNoModificableException(
                    "No se puede pasar una reserva en estado " + estadoReserva + " a " + destino);
        }
        this.estadoReserva = destino;
    }
}
