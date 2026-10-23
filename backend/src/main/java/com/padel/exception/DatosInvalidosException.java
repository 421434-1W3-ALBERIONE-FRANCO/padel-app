package com.padel.exception;

import org.springframework.http.HttpStatus;

public class DatosInvalidosException extends NegocioException {

    public DatosInvalidosException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
