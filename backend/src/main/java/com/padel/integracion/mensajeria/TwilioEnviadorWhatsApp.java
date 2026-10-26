package com.padel.integracion.mensajeria;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Adaptador Twilio de {@link EnviadorWhatsApp}. Sin credenciales el envío se simula (desarrollo). */
@Component
@Slf4j
public class TwilioEnviadorWhatsApp implements EnviadorWhatsApp {

    private final String accountSid;
    private final String authToken;
    private final String remitente;
    private final String prefijoDestino;

    public TwilioEnviadorWhatsApp(@Value("${twilio.account-sid:}") String accountSid,
                                  @Value("${twilio.auth-token:}") String authToken,
                                  @Value("${twilio.whatsapp-from:whatsapp:+14155238886}") String remitente,
                                  @Value("${twilio.whatsapp-to-prefix:whatsapp:}") String prefijoDestino) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.remitente = remitente;
        this.prefijoDestino = prefijoDestino;
    }

    @PostConstruct
    void inicializar() {
        if (configurado()) {
            Twilio.init(accountSid, authToken);
        } else {
            log.warn("Twilio sin credenciales (twilio.account-sid / twilio.auth-token): los WhatsApp se simulan");
        }
    }

    @Override
    public void enviar(String telefono, String texto) {
        String destino = prefijoDestino + telefono.replaceAll("[\\s()-]", "");
        if (!configurado()) {
            log.warn("WhatsApp simulado (Twilio no configurado) para {}", enmascarar(telefono));
            return;
        }
        try {
            Message.creator(new PhoneNumber(destino), new PhoneNumber(remitente), texto).create();
        } catch (ApiException e) {
            throw new EnvioFallidoException("Twilio rechazó el mensaje (código " + e.getCode() + ")", e);
        } catch (RuntimeException e) {
            throw new EnvioFallidoException("No se pudo enviar el WhatsApp: " + e.getMessage(), e);
        }
    }

    private boolean configurado() {
        return accountSid != null && !accountSid.isBlank() && authToken != null && !authToken.isBlank();
    }

    /** "+5491155556666" -> "***6666": los logs no guardan el teléfono completo. */
    static String enmascarar(String telefono) {
        String digitos = telefono == null ? "" : telefono.replaceAll("\\D", "");
        return digitos.length() <= 4 ? "***" : "***" + digitos.substring(digitos.length() - 4);
    }
}
