package com.padel.domain.entity;

import com.padel.domain.enums.TipoNotificacion;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notificaciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TipoNotificacion tipo;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 1000)
    private String mensaje;

    @Builder.Default
    @Column(nullable = false)
    private boolean leida = false;

    @Column(name = "leida_en")
    private LocalDateTime leidaEn;

    // Id de la entidad relacionada (reserva/bono) para deep-link opcional en el frontend.
    @Column(name = "referencia_id")
    private Long referenciaId;
}
