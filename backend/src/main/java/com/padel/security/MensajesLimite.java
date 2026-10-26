package com.padel.security;

/** Textos para el usuario cuando se alcanza un límite de pedidos. */
public final class MensajesLimite {

    private MensajesLimite() {
    }

    /** 45 -> "45 segundos", 90 -> "2 minutos", 3600 -> "60 minutos". */
    public static String duracion(long segundos) {
        if (segundos < 60) {
            return segundos + (segundos == 1 ? " segundo" : " segundos");
        }
        long minutos = (segundos + 59) / 60;
        return minutos + (minutos == 1 ? " minuto" : " minutos");
    }
}
