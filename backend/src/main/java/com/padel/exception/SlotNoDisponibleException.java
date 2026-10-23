package com.padel.exception;

import org.springframework.http.HttpStatus;

public class SlotNoDisponibleException extends NegocioException {

    public SlotNoDisponibleException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
