package com.padel.domain.entity;

import com.padel.domain.enums.CanalMensaje;
import com.padel.domain.enums.EstadoMensaje;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bandeja de salida (patrón Transactional Outbox). Los emails y WhatsApp se guardan acá dentro de la misma
 * transacción que el cambio de negocio y un proceso aparte los envía con reintentos. Antes se mandaban con
 * {@code @Async} desde la transacción: salían aunque después hubiera rollback, se perdían si el servidor se
 * reiniciaba y, con la cola en memoria llena, la excepción tiraba abajo la operación de negocio.
 */
@Entity
@Table(name = "mensajes_salientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MensajeSaliente extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CanalMensaje canal;

    @Column(nullable = false, length = 150)
    private String destinatario;

    @Column(length = 200)
    private String asunto;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String cuerpo;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private EstadoMensaje estado = EstadoMensaje.PENDIENTE;

    @Builder.Default
    @Column(nullable = false)
    private Integer intentos = 0;

    @Column(name = "proximo_intento_en", nullable = false)
    private LocalDateTime proximoIntentoEn;

    @Column(name = "enviado_en")
    private LocalDateTime enviadoEn;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;
}
