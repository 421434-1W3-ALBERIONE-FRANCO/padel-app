package com.padel.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Valida la firma HMAC-SHA256 que MercadoPago envía en el header x-signature de cada
 * webhook, según su esquema documentado: manifest = "id:{data.id};request-id:{x-request-id};ts:{ts};"
 * firmado con el webhook secret. Evita que un tercero simule notificaciones de pago.
 */
@Component
@Slf4j
public class MercadoPagoWebhookValidator {

    @Value("${mercadopago.webhook-secret:}")
    private String webhookSecret;

    public boolean esValida(String xSignature, String xRequestId, String dataId) {
        if (!StringUtils.hasText(webhookSecret)) {
            log.warn("MP_WEBHOOK_SECRET no configurado: se omite la validación de firma del webhook (no usar así en producción)");
            return true;
        }
        if (!StringUtils.hasText(xSignature) || !StringUtils.hasText(dataId)) {
            return false;
        }

        Map<String, String> parts = parseHeader(xSignature);
        String ts = parts.get("ts");
        String v1 = parts.get("v1");
        if (!StringUtils.hasText(ts) || !StringUtils.hasText(v1)) {
            return false;
        }

        StringBuilder manifest = new StringBuilder()
                .append("id:").append(dataId.toLowerCase()).append(";");
        if (StringUtils.hasText(xRequestId)) {
            manifest.append("request-id:").append(xRequestId).append(";");
        }
        manifest.append("ts:").append(ts).append(";");

        String calculado = hmacSha256Hex(manifest.toString(), webhookSecret);
        return calculado != null
                && MessageDigest.isEqual(calculado.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> parseHeader(String xSignature) {
        Map<String, String> parts = new HashMap<>();
        for (String segment : xSignature.split(",")) {
            String[] kv = segment.split("=", 2);
            if (kv.length == 2) {
                parts.put(kv[0].trim(), kv[1].trim());
            }
        }
        return parts;
    }

    private String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.error("Error calculando HMAC de firma de webhook: {}", e.getMessage(), e);
            return null;
        }
    }
}
