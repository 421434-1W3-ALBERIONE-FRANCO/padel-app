package com.padel.security;

import java.time.Instant;

/**
 * Tokens JWT invalidados antes de vencer (logout). Se guarda solo el identificador (jti) hasta el
 * vencimiento del token: después ya no hace falta recordarlo.
 */
public interface RevocacionTokens {

    void revocar(String jti, Instant expira);

    boolean estaRevocado(String jti);
}
