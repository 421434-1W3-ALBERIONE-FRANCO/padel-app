package com.padel.exception;

import org.springframework.http.HttpStatus;

public class PasswordIncorrectoException extends NegocioException {

    public PasswordIncorrectoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
