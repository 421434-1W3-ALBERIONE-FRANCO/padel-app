package com.padel.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Formato de importes para los textos que se le muestran al usuario (avisos, emails, WhatsApp). */
public final class MonedaUtil {

    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");

    private MonedaUtil() {
    }

    /** "$ 12.345,50" */
    public static String formatear(BigDecimal monto) {
        if (monto == null) {
            return "$ 0,00";
        }
        NumberFormat formato = NumberFormat.getCurrencyInstance(ARGENTINA);
        return formato.format(monto);
    }
}
