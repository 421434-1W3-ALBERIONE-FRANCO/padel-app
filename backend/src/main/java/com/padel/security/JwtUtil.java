package com.padel.security;

import com.padel.domain.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Emite y valida los JWT de la API.
 *
 * <ul>
 *   <li>{@code sub} es el id del usuario (antes el email): el token no depende de cómo esté escrito el email.</li>
 *   <li>{@code ver} es la versión de credenciales del usuario: cambiar o resetear la contraseña la incrementa
 *       y todos los tokens anteriores dejan de valer.</li>
 *   <li>{@code jti} identifica al token para poder revocarlo en el logout.</li>
 * </ul>
 */
@Component
public class JwtUtil {

    private static final String EMISOR = "padelapp";
    private static final int MIN_BYTES_SECRETO = 32;

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expirationMs) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_BYTES_SECRETO) {
            throw new IllegalStateException("jwt.secret (JWT_SECRET) debe tener al menos " + MIN_BYTES_SECRETO
                    + " bytes aleatorios (256 bits)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generarToken(Usuario usuario) {
        long ahora = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(EMISOR)
                .subject(String.valueOf(usuario.getId()))
                .id(UUID.randomUUID().toString())
                .claim("rol", usuario.getRol().name())
                .claim("ver", usuario.getTokenVersion())
                .issuedAt(new Date(ahora))
                .expiration(new Date(ahora + expirationMs))
                .signWith(key)
                .compact();
    }

    /** Datos del token si la firma, el emisor y el vencimiento son válidos; vacío en cualquier otro caso. */
    public Optional<TokenInfo> leer(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(EMISOR)
                    .clockSkewSeconds(30)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Integer version = claims.get("ver", Integer.class);
            return Optional.of(new TokenInfo(
                    Long.valueOf(claims.getSubject()),
                    version == null ? 0 : version,
                    claims.getId(),
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            // Firma inválida, vencido, emisor distinto, o un token viejo con el email como subject.
            return Optional.empty();
        }
    }

    public record TokenInfo(Long usuarioId, int version, String jti, Instant expira) {
    }
}
