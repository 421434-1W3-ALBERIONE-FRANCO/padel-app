package com.padel.exception;

import org.springframework.http.HttpStatus;

/** El usuario está autenticado y tiene el rol, pero la operación no se permite en este contexto. */
public class OperacionNoPermitidaException extends NegocioException {

    public OperacionNoPermitidaException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }
}
