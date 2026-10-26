package com.padel.exception;

import org.springframework.http.HttpStatus;

public class RangoHorarioInvalidoException extends NegocioException {

    public RangoHorarioInvalidoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
