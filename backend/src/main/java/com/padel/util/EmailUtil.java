package com.padel.util;

import java.util.Locale;

/**
 * Forma canónica de un email: sin espacios y en minúsculas. Antes "JUAN@x.com" y "juan@x.com" eran cuentas
 * distintas, pero algunas validaciones de dueño comparaban sin distinguir mayúsculas: registrándose con el
 * mismo email en mayúsculas se podía operar sobre recursos ajenos.
 */
public final class EmailUtil {

    private EmailUtil() {
    }

    public static String normalizar(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** "juan.perez@gmail.com" -> "ju***@gmail.com", para logs sin datos personales completos. */
    public static String enmascarar(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int arroba = email.indexOf('@');
        String usuario = email.substring(0, arroba);
        String visible = usuario.length() <= 2 ? usuario.substring(0, 1) : usuario.substring(0, 2);
        return visible + "***" + email.substring(arroba);
    }
}
