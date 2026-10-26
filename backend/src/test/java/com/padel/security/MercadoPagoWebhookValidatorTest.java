package com.padel.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MercadoPagoWebhookValidatorTest {

    private static final String SECRETO = "secreto-de-prueba";

    @Test
    void sinSecretoYConFirmaObligatoriaSeRechazaTodo() {
        MercadoPagoWebhookValidator validador = new MercadoPagoWebhookValidator("", true, 0);
        assertFalse(validador.esValida("ts=1,v1=abc", "req", "123"));
        assertFalse(validador.esValida(null, null, "123"));
    }

    @Test
    void sinSecretoSoloSeAceptaSiSeDesactivoExplicitamenteLaFirma() {
        assertTrue(new MercadoPagoWebhookValidator("", false, 0).esValida(null, null, "123"));
    }

    @Test
    void unaFirmaCorrectaSeAceptaYUnaAlteradaNo() {
        MercadoPagoWebhookValidator validador = new MercadoPagoWebhookValidator(SECRETO, true, 0);
        String ts = "1704908010";
        String firma = firmar("id:123;request-id:req-1;ts:" + ts + ";");

        assertTrue(validador.esValida("ts=" + ts + ",v1=" + firma, "req-1", "123"));
        assertFalse(validador.esValida("ts=" + ts + ",v1=" + firma, "req-1", "124"));
        String alterada = firma.substring(0, firma.length() - 1) + (firma.endsWith("0") ? "1" : "0");
        assertFalse(validador.esValida("ts=" + ts + ",v1=" + alterada, "req-1", "123"));
        assertFalse(validador.esValida("v1=" + firma, "req-1", "123"));
    }

    @Test
    void conVentanaConfiguradaSeRechazaUnaNotificacionVieja() {
        MercadoPagoWebhookValidator validador = new MercadoPagoWebhookValidator(SECRETO, true, 300);
        String viejo = String.valueOf(System.currentTimeMillis() / 1000 - 3600);
        String reciente = String.valueOf(System.currentTimeMillis()); // en milisegundos también se acepta

        assertFalse(validador.esValida("ts=" + viejo + ",v1=" + firmar("id:123;request-id:r;ts:" + viejo + ";"), "r", "123"));
        assertTrue(validador.esValida("ts=" + reciente + ",v1=" + firmar("id:123;request-id:r;ts:" + reciente + ";"), "r", "123"));
    }

    private static String firmar(String manifest) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
