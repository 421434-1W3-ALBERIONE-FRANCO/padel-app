package com.padel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padel.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final LimitePedidosFilter limitePedidosFilter;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    // Orígenes permitidos para CORS y para el WebSocket, separados por coma. Admite patrones.
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    // Swagger UI y /v3/api-docs solo se publican si están habilitados (desarrollo): en producción describían
    // la API completa a cualquiera.
    @Value("${springdoc.api-docs.enabled:false}")
    private boolean documentacionHabilitada;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<String> publicos = new ArrayList<>(List.of(
                "/api/v1/auth/**",
                "/api/v1/pagos/webhook/**",
                "/api/v1/health",
                // Handshake del WebSocket de disponibilidad: el navegador no puede mandar el header Authorization
                // al abrir un WebSocket y el canal solo difunde "cambió la cancha X" (sin datos sensibles).
                "/ws/**",
                "/error",
                // Probes de salud y métricas. En producción /actuator está en el puerto de management (8081),
                // que solo es accesible dentro de la red de Docker: el puerto público no lo expone.
                "/actuator/health", "/actuator/health/**", "/actuator/prometheus", "/actuator/info"));
        if (documentacionHabilitada) {
            publicos.addAll(List.of("/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html"));
        }

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // API stateless con token en el header Authorization (no cookies): CSRF no aplica.
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .authorizeHttpRequests(req -> req
                        .requestMatchers(publicos.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated()
                )
                // Sin entry point propio Spring Security responde 403 a las peticiones sin token o con el token
                // vencido, y el frontend (que cierra la sesión ante un 401) dejaba al usuario trabado.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> escribirError(request, response,
                                HttpStatus.UNAUTHORIZED, "Tu sesión no es válida o expiró. Iniciá sesión nuevamente."))
                        .accessDeniedHandler((request, response, e) -> escribirError(request, response,
                                HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este recurso"))
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(limitePedidosFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void escribirError(HttpServletRequest request, HttpServletResponse response,
                               HttpStatus status, String mensaje) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiError error = new ApiError(LocalDateTime.now(), status.value(), status.getReasonPhrase(),
                mensaje, request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), error);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(origenesPermitidos());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        configuration.setExposedHeaders(List.of("Retry-After"));
        // El token viaja en el header Authorization, no en cookies: no hace falta permitir credenciales
        // (con ellas y un patrón como https://*.netlify.app cualquier sitio de Netlify quedaba habilitado).
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    public List<String> origenesPermitidos() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}
