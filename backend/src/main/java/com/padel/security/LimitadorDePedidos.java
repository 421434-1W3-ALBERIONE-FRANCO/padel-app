package com.padel.security;

import java.time.Duration;

/**
 * Límite de pedidos por clave (IP, email, usuario) en ventanas fijas. Protege contra fuerza bruta en el
 * login, envío masivo de emails de recuperación y altas de reservas en ráfaga.
 */
public interface LimitadorDePedidos {

    /**
     * Registra un pedido. Devuelve 0 si está dentro del límite o, si lo superó, los segundos que faltan
     * para que se libere la ventana.
     */
    long consumir(String clave, int maximo, Duration ventana);

    /** Igual que {@link #consumir} pero sin registrar el pedido (para chequear antes de intentar). */
    long segundosBloqueado(String clave, int maximo);

    void reiniciar(String clave);
}
