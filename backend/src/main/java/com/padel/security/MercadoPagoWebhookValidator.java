package com.padel.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Valida la firma HMAC-SHA256 que MercadoPago envía en el header x-signature de cada
 * webhook, según su esquema documentado: manifest = "id:{data.id};request-id:{x-request-id};ts:{ts};"
 * firmado con el webhook secret. Evita que un tercero simule notificaciones de pago.
 *
 * <p>Sin secreto configurado antes se aceptaba cualquier notificación; ahora, salvo que se lo permita
 * explícitamente para desarrollo ({@code mercadopago.webhook.exigir-firma=false}), se rechazan todas.
 */
@Component
@Slf4j
public class MercadoPagoWebhookValidator {

    private final String webhookSecret;
    private final boolean exigirFirma;
    private final long toleranciaSegundos;

    public MercadoPagoWebhookValidator(
            @Value("${mercadopago.webhook-secret:}") String webhookSecret,
            @Value("${mercadopago.webhook.exigir-firma:true}") boolean exigirFirma,
            // Antigüedad máxima del ts firmado (0 = sin ventana). MercadoPago no documenta si los reintentos
            // conservan el ts original, y el reproceso es idempotente y consulta el estado real del pago.
            @Value("${mercadopago.webhook.tolerancia-segundos:0}") long toleranciaSegundos) {
        this.webhookSecret = webhookSecret;
        this.exigirFirma = exigirFirma;
        this.toleranciaSegundos = toleranciaSegundos;
    }

    @PostConstruct
    void advertirSiFaltaSecreto() {
        if (!StringUtils.hasText(webhookSecret)) {
            if (exigirFirma) {
                log.error("MP_WEBHOOK_SECRET no está configurado: se van a rechazar todas las notificaciones de MercadoPago");
            } else {
                log.warn("MP_WEBHOOK_SECRET no está configurado y la firma no es obligatoria: usar solo en desarrollo");
            }
        }
    }

    public boolean esValida(String xSignature, String xRequestId, String dataId) {
        if (!StringUtils.hasText(webhookSecret)) {
            return !exigirFirma;
        }
        if (!StringUtils.hasText(xSignature) || !StringUtils.hasText(dataId)) {
            return false;
        }

        Map<String, String> parts = parseHeader(xSignature);
        String ts = parts.get("ts");
        String v1 = parts.get("v1");
        if (!StringUtils.hasText(ts) || !StringUtils.hasText(v1) || !dentroDeLaVentana(ts)) {
            return false;
        }

        StringBuilder manifest = new StringBuilder()
                .append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(";");
        if (StringUtils.hasText(xRequestId)) {
            manifest.append("request-id:").append(xRequestId).append(";");
        }
        manifest.append("ts:").append(ts).append(";");

        String calculado = hmacSha256Hex(manifest.toString(), webhookSecret);
        return calculado != null
                && MessageDigest.isEqual(calculado.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));
    }

    private boolean dentroDeLaVentana(String ts) {
        if (toleranciaSegundos <= 0) {
            return true;
        }
        try {
            long valor = Long.parseLong(ts);
            // Según el país la documentación habla de segundos o de milisegundos: se aceptan ambos.
            long segundos = valor > 100_000_000_000L ? valor / 1000 : valor;
            long ahora = System.currentTimeMillis() / 1000;
            return Math.abs(ahora - segundos) <= toleranciaSegundos;
        } catch (NumberFormatException e) {
            return false;
        }
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
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            log.error("Error calculando HMAC de firma de webhook: {}", e.getMessage(), e);
            return null;
        }
    }
}
