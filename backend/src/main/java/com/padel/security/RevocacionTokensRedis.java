package com.padel.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

/** {@link RevocacionTokens} sobre Redis (compartido entre instancias), con respaldo en memoria. */
@Slf4j
public class RevocacionTokensRedis implements RevocacionTokens {

    private static final String PREFIJO = "padelapp:jwt-revocado:";

    private final StringRedisTemplate redis;
    private final RevocacionTokensEnMemoria respaldo = new RevocacionTokensEnMemoria();

    public RevocacionTokensRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void revocar(String jti, Instant expira) {
        if (jti == null || expira == null) {
            return;
        }
        // Se guarda también en memoria: si Redis se cae después, esta instancia sigue rechazando el token.
        respaldo.revocar(jti, expira);
        Duration restante = Duration.between(Instant.now(), expira);
        if (restante.isNegative() || restante.isZero()) {
            return;
        }
        try {
            redis.opsForValue().set(PREFIJO + jti, "1", restante);
        } catch (DataAccessException e) {
            log.warn("Redis no disponible al revocar un token; queda revocado solo en esta instancia: {}", e.getMessage());
        }
    }

    @Override
    public boolean estaRevocado(String jti) {
        if (jti == null) {
            return false;
        }
        if (respaldo.estaRevocado(jti)) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIJO + jti));
        } catch (DataAccessException e) {
            return false;
        }
    }
}
