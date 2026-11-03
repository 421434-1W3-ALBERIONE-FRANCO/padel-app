package com.padel.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** La foto de perfil de un jugador, ya normalizada (JPEG cuadrado). Una por perfil. */
@Entity
@Table(name = "fotos_perfil")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FotoPerfil {

    @Id
    @Column(name = "perfil_id")
    private Long perfilId;

    // Sin @Lob: en PostgreSQL un byte[] se mapea a bytea (con @Lob sería un large object aparte).
    @Column(nullable = false, length = 1_048_576)
    private byte[] contenido;

    @Column(name = "tipo_contenido", nullable = false, length = 30)
    private String tipoContenido;

    @Column(name = "actualizada_en", nullable = false)
    private LocalDateTime actualizadaEn;
}
