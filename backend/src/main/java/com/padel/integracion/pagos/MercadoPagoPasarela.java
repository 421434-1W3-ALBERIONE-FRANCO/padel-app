package com.padel.integracion.pagos;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.client.point.PointClient;
import com.mercadopago.client.point.PointPaymentIntentAdditionalInfoRequest;
import com.mercadopago.client.point.PointPaymentIntentRequest;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Adaptador de {@link PasarelaPagos} sobre el SDK oficial de MercadoPago. */
@Component
public class MercadoPagoPasarela implements PasarelaPagos {

    private static final int TIMEOUT_MS = 10_000;

    private final String accessToken;

    public MercadoPagoPasarela(@Value("${mercadopago.access-token}") String accessToken) {
        this.accessToken = accessToken;
    }

    @PostConstruct
    void configurar() {
        MercadoPagoConfig.setAccessToken(accessToken);
        // Sin timeouts explícitos, un MercadoPago lento dejaba colgados los hilos del servidor.
        MercadoPagoConfig.setConnectionTimeout(TIMEOUT_MS);
        MercadoPagoConfig.setConnectionRequestTimeout(TIMEOUT_MS);
        MercadoPagoConfig.setSocketTimeout(TIMEOUT_MS);
    }

    @Override
    public PreferenciaCreada crearPreferencia(NuevaPreferencia p) {
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .id(p.idItem())
                .title(p.titulo())
                .quantity(1)
                .currencyId("ARS")
                .unitPrice(p.precio())
                .build();
        PreferenceRequest.PreferenceRequestBuilder builder = PreferenceRequest.builder()
                .items(List.of(item))
                .backUrls(PreferenceBackUrlsRequest.builder()
                        .success(p.urlExito())
                        .failure(p.urlFallo())
                        .pending(p.urlPendiente())
                        .build())
                .externalReference(p.referenciaExterna())
                .autoReturn("approved")
                // Aprobado o rechazado en el momento: sin pagos "en proceso" que se acrediten con el turno ya vencido.
                .binaryMode(true);
        if (p.venceEl() != null) {
            builder.expires(true).expirationDateTo(p.venceEl());
        }
        try {
            Preference preference = new PreferenceClient().create(builder.build());
            return new PreferenciaCreada(preference.getId(), preference.getInitPoint());
        } catch (MPApiException e) {
            throw new PasarelaPagosException("MercadoPago rechazó la preferencia (HTTP " + e.getStatusCode() + ")", e);
        } catch (MPException e) {
            throw new PasarelaPagosException("No se pudo comunicar con MercadoPago", e);
        }
    }

    @Override
    public PagoExterno consultarPago(String idPago) {
        try {
            Payment payment = new PaymentClient().get(Long.parseLong(idPago));
            return new PagoExterno(String.valueOf(payment.getId()), payment.getStatus(), payment.getExternalReference(),
                    payment.getTransactionAmount(), payment.getCurrencyId());
        } catch (NumberFormatException e) {
            throw new PasarelaPagosException("Identificador de pago inválido: " + idPago, e);
        } catch (MPApiException e) {
            throw new PasarelaPagosException("MercadoPago no devolvió el pago " + idPago + " (HTTP " + e.getStatusCode() + ")", e);
        } catch (MPException e) {
            throw new PasarelaPagosException("No se pudo consultar el pago " + idPago + " en MercadoPago", e);
        }
    }

    @Override
    public void reembolsar(String idPago, String claveIdempotencia) {
        MPRequestOptions opciones = MPRequestOptions.builder()
                .customHeaders(Map.of("X-Idempotency-Key", claveIdempotencia))
                .build();
        try {
            new PaymentRefundClient().refund(Long.parseLong(idPago), opciones);
        } catch (NumberFormatException e) {
            throw new PasarelaPagosException("Identificador de pago inválido: " + idPago, e);
        } catch (MPApiException e) {
            throw new PasarelaPagosException("MercadoPago rechazó el reintegro del pago " + idPago + " (HTTP " + e.getStatusCode() + ")", e);
        } catch (MPException e) {
            throw new PasarelaPagosException("No se pudo pedir el reintegro del pago " + idPago, e);
        }
    }

    @Override
    public void enviarCobroAlPoint(String idDispositivo, BigDecimal monto, String descripcion, String referenciaExterna) {
        PointPaymentIntentRequest request = PointPaymentIntentRequest.builder()
                .amount(monto)
                .description(descripcion)
                .additionalInfo(PointPaymentIntentAdditionalInfoRequest.builder()
                        .externalReference(referenciaExterna)
                        .printOnTerminal(true)
                        .build())
                .build();
        try {
            new PointClient().createPaymentIntent(idDispositivo, request);
        } catch (MPApiException e) {
            throw new PasarelaPagosException("La terminal Point rechazó el cobro (HTTP " + e.getStatusCode() + ")", e);
        } catch (MPException e) {
            throw new PasarelaPagosException("No se pudo enviar el cobro a la terminal Point", e);
        }
    }
}
