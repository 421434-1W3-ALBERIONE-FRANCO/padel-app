package com.padel.exception;

import org.springframework.http.HttpStatus;

/**
 * Base de las excepciones de negocio: cada una declara su status HTTP y, si el frontend necesita
 * distinguirla para reaccionar distinto (no solo mostrar el mensaje), un código estable.
 */
public abstract class NegocioException extends RuntimeException {

    protected NegocioException(String message) {
        super(message);
    }

    public abstract HttpStatus getStatus();

    /** Código estable para el frontend; null si alcanza con el status y el mensaje. */
    public String getCodigo() {
        return null;
    }
}
