package com.padel.domain.entity;

import com.padel.domain.enums.RolUsuario;
import com.padel.util.EmailUtil;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    // Siempre en minúsculas (ver normalizarEmail y el índice único sobre lower(email) de V36).
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 50)
    private String telefono;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RolUsuario rol;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    // Las cuentas creadas por el registro público confirman el email antes de poder iniciar sesión.
    @Builder.Default
    @Column(name = "email_verificado", nullable = false)
    private boolean emailVerificado = true;

    // Versión de credenciales: viaja en el JWT y se incrementa al cambiar o resetear la contraseña, lo que
    // invalida todas las sesiones abiertas.
    @Builder.Default
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    public void invalidarSesiones() {
        tokenVersion++;
    }

    @PrePersist
    @PreUpdate
    void normalizarEmail() {
        email = EmailUtil.normalizar(email);
    }
}
