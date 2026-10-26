package com.padel.exception;

import org.springframework.http.HttpStatus;

/** Un bloqueo de cancha pisa reservas vigentes: el staff tiene que confirmar que se cancelen. */
public class ReservasAfectadasException extends NegocioException {

    public static final String CODIGO = "RESERVAS_AFECTADAS";

    public ReservasAfectadasException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String getCodigo() {
        return CODIGO;
    }
}
