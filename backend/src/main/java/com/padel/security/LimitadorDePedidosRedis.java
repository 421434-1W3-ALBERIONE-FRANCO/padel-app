package com.padel.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * {@link LimitadorDePedidos} sobre Redis (contador por ventana fija, compartido entre instancias). Si Redis
 * no responde se usa el contador en memoria: un límite aproximado es preferible a dejar el login sin
 * protección o caído.
 */
@Slf4j
public class LimitadorDePedidosRedis implements LimitadorDePedidos {

    private static final String PREFIJO = "padelapp:limite:";

    private final StringRedisTemplate redis;
    private final LimitadorEnMemoria respaldo = new LimitadorEnMemoria();

    public LimitadorDePedidosRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long consumir(String clave, int maximo, Duration ventana) {
        try {
            String key = PREFIJO + clave;
            Long cantidad = redis.opsForValue().increment(key);
            if (cantidad != null && cantidad == 1L) {
                redis.expire(key, ventana);
            }
            if (cantidad == null || cantidad <= maximo) {
                return 0;
            }
            Long ttl = redis.getExpire(key);
            if (ttl == null || ttl < 0) {
                // Ventana sin vencimiento (se cayó entre el INCR y el EXPIRE): se la vuelve a acotar.
                redis.expire(key, ventana);
                return ventana.toSeconds();
            }
            return Math.max(1, ttl);
        } catch (DataAccessException e) {
            log.warn("Redis no disponible para el límite de pedidos; se usa el contador en memoria: {}", e.getMessage());
            return respaldo.consumir(clave, maximo, ventana);
        }
    }

    @Override
    public long segundosBloqueado(String clave, int maximo) {
        try {
            String key = PREFIJO + clave;
            String valor = redis.opsForValue().get(key);
            if (valor == null || Long.parseLong(valor) < maximo) {
                return 0;
            }
            Long ttl = redis.getExpire(key);
            return ttl == null || ttl < 0 ? 0 : Math.max(1, ttl);
        } catch (DataAccessException e) {
            return respaldo.segundosBloqueado(clave, maximo);
        }
    }

    @Override
    public void reiniciar(String clave) {
        respaldo.reiniciar(clave);
        try {
            redis.delete(PREFIJO + clave);
        } catch (DataAccessException e) {
            log.debug("Redis no disponible al reiniciar el límite {}", clave);
        }
    }
}
