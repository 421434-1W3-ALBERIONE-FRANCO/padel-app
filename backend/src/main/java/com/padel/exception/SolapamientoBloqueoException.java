package com.padel.exception;

import org.springframework.http.HttpStatus;

public class SolapamientoBloqueoException extends NegocioException {

    public SolapamientoBloqueoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
