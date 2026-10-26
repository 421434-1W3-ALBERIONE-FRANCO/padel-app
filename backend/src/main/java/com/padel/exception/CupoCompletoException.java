package com.padel.exception;

import org.springframework.http.HttpStatus;

public class CupoCompletoException extends NegocioException {

    public CupoCompletoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
