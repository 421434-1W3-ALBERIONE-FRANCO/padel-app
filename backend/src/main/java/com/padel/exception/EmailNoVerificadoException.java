package com.padel.exception;

import org.springframework.http.HttpStatus;

/** Credenciales correctas pero la cuenta todavía no confirmó su email. */
public class EmailNoVerificadoException extends NegocioException {

    public static final String CODIGO = "EMAIL_NO_VERIFICADO";

    public EmailNoVerificadoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }

    @Override
    public String getCodigo() {
        return CODIGO;
    }
}
