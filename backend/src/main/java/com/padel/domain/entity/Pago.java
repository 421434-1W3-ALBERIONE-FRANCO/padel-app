package com.padel.domain.entity;

import com.padel.domain.enums.ConceptoPago;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.MetodoPago;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pagos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pago extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Null solo en la venta de un bono: ese pago apunta al bono vendido.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id")
    private Reserva reserva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bono_id")
    private Bono bono;

    // Separa el pago del turno del de los consumos (los dos apuntan a la misma reserva) y de la venta de bonos.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ConceptoPago concepto = ConceptoPago.TURNO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPago metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPago estado;

    @Column(name = "mp_preference_id")
    private String mpPreferenceId;

    @Column(name = "mp_payment_id", unique = true)
    private String mpPaymentId;

    // Staff (RECEPCIONISTA/ADMIN) que atendió el cobro presencial; null si el jugador pagó solo, online.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendido_por_id")
    private Usuario atendidoPor;
}
