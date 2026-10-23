package com.padel.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends NegocioException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
