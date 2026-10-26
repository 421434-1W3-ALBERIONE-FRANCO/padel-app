package com.padel.exception;

import org.springframework.http.HttpStatus;

public class NombreDuplicadoException extends NegocioException {

    public NombreDuplicadoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
