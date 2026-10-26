package com.padel.integracion.mensajeria;

/** No se pudo entregar un mensaje al proveedor (SMTP, Twilio); el despachador lo reintenta. */
public class EnvioFallidoException extends RuntimeException {

    public EnvioFallidoException(String message, Throwable cause) {
        super(message, cause);
    }
}
