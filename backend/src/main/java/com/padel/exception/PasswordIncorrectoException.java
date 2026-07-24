package com.padel.exception;

public class PasswordIncorrectoException extends RuntimeException {
    public PasswordIncorrectoException(String message) {
        super(message);
    }
}
