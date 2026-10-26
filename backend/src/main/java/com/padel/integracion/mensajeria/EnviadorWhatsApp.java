package com.padel.integracion.mensajeria;

/** Puerto de envío de mensajes de WhatsApp (el adaptador real es Twilio). */
public interface EnviadorWhatsApp {

    void enviar(String telefono, String texto);
}
