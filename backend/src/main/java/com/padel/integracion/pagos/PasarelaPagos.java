package com.padel.integracion.pagos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Puerto hacia la pasarela de pagos (patrón Ports &amp; Adapters). Los servicios de negocio dependen de esta
 * interfaz y no del SDK de MercadoPago: se pueden probar sin red y cambiar de proveedor sin tocarlos.
 */
public interface PasarelaPagos {

    PreferenciaCreada crearPreferencia(NuevaPreferencia preferencia);

    PagoExterno consultarPago(String idPago);

    /**
     * Devuelve el total de un pago aprobado. {@code claveIdempotencia} evita reintegrar dos veces si la
     * operación se reintenta.
     */
    void reembolsar(String idPago, String claveIdempotencia);

    /** Envía un cobro presencial a la terminal Point indicada. */
    void enviarCobroAlPoint(String idDispositivo, BigDecimal monto, String descripcion, String referenciaExterna);

    record NuevaPreferencia(
            String idItem,
            String titulo,
            BigDecimal precio,
            String referenciaExterna,
            String urlExito,
            String urlFallo,
            String urlPendiente,
            OffsetDateTime venceEl
    ) {
    }

    record PreferenciaCreada(String id, String urlCheckout) {
    }

    /** Estado de un pago según la pasarela (fuente de verdad: nunca se confía en el cuerpo del webhook). */
    record PagoExterno(
            String id,
            String estado,
            String referenciaExterna,
            BigDecimal monto,
            String moneda
    ) {
        public boolean aprobado() {
            return "approved".equalsIgnoreCase(estado);
        }

        public boolean rechazadoOCancelado() {
            return "rejected".equalsIgnoreCase(estado) || "cancelled".equalsIgnoreCase(estado);
        }
    }
}
