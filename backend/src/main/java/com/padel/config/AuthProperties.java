package com.padel.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Reglas de cuentas y credenciales ({@code app.auth.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /**
     * Las cuentas del registro público tienen que confirmar el email antes de iniciar sesión. Sin esto
     * cualquiera podía registrar cuentas con emails ajenos (y usarlas para acaparar turnos).
     */
    private boolean verificacionEmailObligatoria = true;

    /** Validez del enlace de confirmación de email. */
    private int horasValidezVerificacion = 24;

    /** Validez del enlace de recuperación de contraseña. */
    private int horasValidezRecuperacion = 1;
}
