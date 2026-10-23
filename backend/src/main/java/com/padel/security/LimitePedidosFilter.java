package com.padel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padel.config.LimitesProperties;
import com.padel.exception.ApiError;
import com.padel.exception.DemasiadosIntentosException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;

/**
 * Límite de pedidos por IP en los endpoints públicos de autenticación (antes no había ninguno: se podía
 * probar contraseñas o disparar emails de recuperación sin freno). Responde 429 con Retry-After y el mismo
 * formato de error que el resto de la API.
 */
@Component
@RequiredArgsConstructor
public class LimitePedidosFilter extends OncePerRequestFilter {

    private static final Map<String, Function<LimitesProperties, LimitesProperties.Regla>> REGLAS = Map.of(
            "/api/v1/auth/login", LimitesProperties::getLogin,
            "/api/v1/auth/registro", LimitesProperties::getRegistro,
            "/api/v1/auth/recuperar-password", LimitesProperties::getRecuperacion,
            "/api/v1/auth/resetear-password", LimitesProperties::getTokens,
            "/api/v1/auth/verificacion-email", LimitesProperties::getTokens,
            "/api/v1/auth/verificacion-email/reenvio", LimitesProperties::getRecuperacion
    );

    private final LimitesProperties limites;
    private final LimitadorDePedidos limitador;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !limites.isHabilitado()
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !REGLAS.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ruta = request.getRequestURI();
        LimitesProperties.Regla regla = REGLAS.get(ruta).apply(limites);
        // Con server.forward-headers-strategy=native, getRemoteAddr() ya es la IP real del cliente detrás del
        // proxy (y solo si el proxy es de la red interna: un cliente no puede falsear X-Forwarded-For).
        long espera = limitador.consumir("ip:" + ruta + ":" + request.getRemoteAddr(), regla.getMaximo(), regla.getVentana());
        if (espera > 0) {
            responderDemasiadosPedidos(request, response, espera);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void responderDemasiadosPedidos(HttpServletRequest request, HttpServletResponse response, long espera) throws IOException {
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(espera));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiError error = new ApiError(LocalDateTime.now(), status.value(), status.getReasonPhrase(),
                "Demasiados intentos. Probá de nuevo en " + MensajesLimite.duracion(espera) + ".",
                request.getRequestURI(), DemasiadosIntentosException.CODIGO);
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
