package com.padel.exception;

import org.springframework.http.HttpStatus;

public class StockInsuficienteException extends NegocioException {

    public StockInsuficienteException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
