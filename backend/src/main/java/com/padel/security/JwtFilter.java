package com.padel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autentica el request a partir del header Authorization. El token se valida una sola vez y además se
 * exige que: el usuario siga activo, la versión de credenciales del token coincida con la actual (cambio o
 * reseteo de contraseña) y el token no haya sido revocado (logout).
 */
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final RevocacionTokens revocacionTokens;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(PREFIJO)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtUtil.leer(authHeader.substring(PREFIJO.length()))
                    .filter(info -> !revocacionTokens.estaRevocado(info.jti()))
                    .flatMap(info -> userDetailsService.cargarPorId(info.usuarioId())
                            .filter(CustomUserDetails::isEnabled)
                            .filter(detalles -> detalles.getUsuario().getTokenVersion() == info.version()))
                    .ifPresent(detalles -> {
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                detalles, null, detalles.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        filterChain.doFilter(request, response);
    }
}
