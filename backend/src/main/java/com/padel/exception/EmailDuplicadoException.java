package com.padel.exception;

import org.springframework.http.HttpStatus;

public class EmailDuplicadoException extends NegocioException {

    public EmailDuplicadoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
