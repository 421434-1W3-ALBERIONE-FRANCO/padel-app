package com.padel.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación en memoria de {@link LimitadorDePedidos}. Sirve de respaldo cuando Redis no está disponible
 * (con una sola instancia del backend alcanza; con varias, cada una cuenta por su lado).
 */
public class LimitadorEnMemoria implements LimitadorDePedidos {

    private static final int LIMPIAR_CADA = 1_000;

    private record Ventana(long cantidad, long venceEnMillis) {
    }

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();
    private long operaciones;

    @Override
    public long consumir(String clave, int maximo, Duration ventana) {
        long ahora = System.currentTimeMillis();
        Ventana actual = ventanas.compute(clave, (k, v) -> v == null || v.venceEnMillis() <= ahora
                ? new Ventana(1, ahora + ventana.toMillis())
                : new Ventana(v.cantidad() + 1, v.venceEnMillis()));
        limpiarDeVezEnCuando(ahora);
        return actual.cantidad() > maximo ? segundosHasta(actual.venceEnMillis(), ahora) : 0;
    }

    @Override
    public long segundosBloqueado(String clave, int maximo) {
        long ahora = System.currentTimeMillis();
        Ventana actual = ventanas.get(clave);
        if (actual == null || actual.venceEnMillis() <= ahora || actual.cantidad() < maximo) {
            return 0;
        }
        return segundosHasta(actual.venceEnMillis(), ahora);
    }

    @Override
    public void reiniciar(String clave) {
        ventanas.remove(clave);
    }

    private static long segundosHasta(long venceEnMillis, long ahora) {
        return Math.max(1, (venceEnMillis - ahora + 999) / 1000);
    }

    private synchronized void limpiarDeVezEnCuando(long ahora) {
        if (++operaciones % LIMPIAR_CADA == 0) {
            ventanas.entrySet().removeIf(e -> e.getValue().venceEnMillis() <= ahora);
        }
    }
}
