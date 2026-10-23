package com.padel.domain.entity;

import com.padel.domain.enums.EstadoBono;
import com.padel.exception.BonoNoDisponibleException;
import com.padel.exception.DatosInvalidosException;
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

    @Version
    private Long version;

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

    // Tipo del catálogo con que se vendió (null en bonos anteriores al catálogo). Horas, precio y vencimiento se
    // copian al vender: si después cambia el catálogo, el bono vendido no cambia.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_bono_id")
    private TipoBono tipoBono;

    // Solo en bonos sin cargo: por qué se regaló (queda para auditoría).
    @Column(name = "motivo_sin_cargo", length = 255)
    private String motivoSinCargo;

    // Staff (RECEPCIONISTA/ADMIN) que vendió/asignó el bono; base para el cálculo de comisiones.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendido_por_id")
    private Usuario vendidoPor;

    public int saldoHoras() {
        return horasTotales - horasUsadas;
    }

    public boolean estaVencidoAl(LocalDate fecha) {
        return fechaVencimiento.isBefore(fecha);
    }

    /** Descuenta horas validando estado y saldo: una duración negativa ya no puede "sumar" horas al bono. */
    public void descontarHoras(int horas) {
        if (horas <= 0) {
            throw new DatosInvalidosException("Las horas a descontar de un bono deben ser mayores a cero");
        }
        if (estado != EstadoBono.ACTIVO || saldoHoras() < horas) {
            throw new BonoNoDisponibleException("El bono no tiene saldo suficiente (" + horas + " hs requeridas)");
        }
        horasUsadas += horas;
        if (horasUsadas.equals(horasTotales)) {
            estado = EstadoBono.AGOTADO;
        }
    }

    /** Devuelve horas de un turno cancelado; un bono agotado vuelve a estar activo si no venció. */
    public void reintegrarHoras(int horas, LocalDate hoy) {
        if (horas <= 0) {
            throw new DatosInvalidosException("Las horas a reintegrar a un bono deben ser mayores a cero");
        }
        horasUsadas = Math.max(0, horasUsadas - horas);
        if (estado == EstadoBono.AGOTADO && !estaVencidoAl(hoy)) {
            estado = EstadoBono.ACTIVO;
        }
    }
}
