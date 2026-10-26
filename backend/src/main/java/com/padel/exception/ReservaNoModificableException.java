package com.padel.exception;

import org.springframework.http.HttpStatus;

public class ReservaNoModificableException extends NegocioException {

    public ReservaNoModificableException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
