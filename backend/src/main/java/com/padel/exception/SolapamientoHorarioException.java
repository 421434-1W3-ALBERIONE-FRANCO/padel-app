package com.padel.exception;

import org.springframework.http.HttpStatus;

public class SolapamientoHorarioException extends NegocioException {

    public SolapamientoHorarioException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
