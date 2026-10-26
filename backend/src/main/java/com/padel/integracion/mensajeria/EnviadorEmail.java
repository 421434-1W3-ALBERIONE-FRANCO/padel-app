package com.padel.integracion.mensajeria;

/** Puerto de envío de emails (el adaptador real es SMTP). */
public interface EnviadorEmail {

    /** Envía el email; si falla lanza {@link EnvioFallidoException} para que el despachador reintente. */
    void enviar(String destinatario, String asunto, String html);
}
