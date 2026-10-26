package com.padel.exception;

import org.springframework.http.HttpStatus;

public class EstadoInvalidoException extends NegocioException {

    public EstadoInvalidoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
