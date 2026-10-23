package com.padel.exception;

import org.springframework.http.HttpStatus;

public class BonoNoDisponibleException extends NegocioException {

    public BonoNoDisponibleException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
