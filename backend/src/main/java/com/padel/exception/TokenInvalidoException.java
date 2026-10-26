package com.padel.exception;

import org.springframework.http.HttpStatus;

public class TokenInvalidoException extends NegocioException {

    public TokenInvalidoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
