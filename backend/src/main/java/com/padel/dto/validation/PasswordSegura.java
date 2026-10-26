package com.padel.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Contraseña nueva aceptable: entre 10 y 72 bytes (límite de BCrypt) y fuera de la lista de contraseñas
 * más usadas. No se exigen combinaciones de caracteres (guía NIST 800-63B): la longitud pesa más.
 * El mínimo anterior era de 6 caracteres. Las contraseñas existentes siguen sirviendo para el login.
 */
@Documented
@Constraint(validatedBy = PasswordSeguraValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordSegura {

    String message() default "La contraseña no es segura";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
