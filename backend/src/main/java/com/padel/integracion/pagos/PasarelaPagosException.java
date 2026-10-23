package com.padel.integracion.pagos;

/** Falla de comunicación o rechazo de la pasarela de pagos. */
public class PasarelaPagosException extends RuntimeException {

    public PasarelaPagosException(String message, Throwable cause) {
        super(message, cause);
    }

    public PasarelaPagosException(String message) {
        super(message);
    }
}
