package com.padel.domain.entity;

import com.padel.domain.enums.TipoTokenUsuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Token de un solo uso enviado por email (recuperar la contraseña o confirmar el email). Se guarda solo el
 * hash SHA-256: quien lea la base no puede usar los tokens vigentes.
 */
@Entity
@Table(name = "tokens_recuperacion_password")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenUsuario extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 30)
    private TipoTokenUsuario tipo = TipoTokenUsuario.RECUPERACION_PASSWORD;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Builder.Default
    @Column(nullable = false)
    private boolean usado = false;

    public boolean vigenteAl(LocalDateTime momento) {
        return !usado && expiraEn.isAfter(momento);
    }
}
