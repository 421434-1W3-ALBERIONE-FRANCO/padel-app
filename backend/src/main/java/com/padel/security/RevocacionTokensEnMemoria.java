package com.padel.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** {@link RevocacionTokens} en memoria: respaldo sin Redis (con una sola instancia del backend es suficiente). */
public class RevocacionTokensEnMemoria implements RevocacionTokens {

    private final Map<String, Instant> revocados = new ConcurrentHashMap<>();

    @Override
    public void revocar(String jti, Instant expira) {
        Instant ahora = Instant.now();
        revocados.entrySet().removeIf(e -> e.getValue().isBefore(ahora));
        if (jti != null && expira != null && expira.isAfter(ahora)) {
            revocados.put(jti, expira);
        }
    }

    @Override
    public boolean estaRevocado(String jti) {
        if (jti == null) {
            return false;
        }
        Instant expira = revocados.get(jti);
        return expira != null && expira.isAfter(Instant.now());
    }
}
