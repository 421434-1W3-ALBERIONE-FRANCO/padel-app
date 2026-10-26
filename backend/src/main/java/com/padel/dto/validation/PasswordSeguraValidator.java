package com.padel.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public class PasswordSeguraValidator implements ConstraintValidator<PasswordSegura, String> {

    static final int MINIMO = 10;
    static final int MAXIMO_BYTES = 72;

    // Las más frecuentes en filtraciones (y variantes obvias del dominio) que cumplen el largo mínimo.
    private static final Set<String> COMUNES = Set.of(
            "1234567890", "0123456789", "12345678910", "123456789a", "a123456789", "1111111111", "0000000000",
            "qwertyuiop", "qwerty1234", "qwerty12345", "asdfghjkl1", "asdfghjklñ", "zxcvbnm123", "abcdefghij",
            "password12", "password123", "password1234", "contraseña", "contrasena1", "contrasena123",
            "iloveyou12", "iloveyou123", "administrador", "admin12345", "admin123456", "bienvenido1",
            "padelpadel", "padel12345", "padel123456", "padelapp123", "futbol1234", "argentina1", "boca123456",
            "river123456");

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        if (valor == null || valor.isEmpty()) {
            return true; // de los obligatorios se ocupa @NotBlank
        }
        String error = null;
        if (valor.length() < MINIMO) {
            error = "La contraseña debe tener al menos " + MINIMO + " caracteres";
        } else if (valor.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
            error = "La contraseña no puede superar los " + MAXIMO_BYTES + " caracteres";
        } else if (COMUNES.contains(valor.toLowerCase(Locale.ROOT)) || valor.chars().distinct().count() <= 2) {
            error = "La contraseña es demasiado común o fácil de adivinar";
        }
        if (error == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(error).addConstraintViolation();
        return false;
    }
}
