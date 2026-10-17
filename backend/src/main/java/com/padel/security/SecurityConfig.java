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
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    // Origenes permitidos para CORS, separados por coma. Admite patrones (p.ej. https://*.netlify.app).
    // Se inyecta por variable de entorno en prod; default localhost para desarrollo.
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                // sameOrigin (no disable): permite el iframe de /h2-console (mismo origen) sin abrir
                // la API entera a clickjacking desde sitios de terceros.
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(req -> req
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/pagos/webhook/**",
                                "/api/v1/health",
                                // Handshake del WebSocket de disponibilidad: el navegador no puede mandar el
                                // header Authorization al abrir un WebSocket, y el canal solo difunde avisos
                                // de "cambió la cancha X" (sin datos sensibles) del servidor a los clientes.
                                "/ws/**",
                                "/error",
                                "/h2-console/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // Sin entry point propio Spring Security responde 403 a las peticiones sin token o con
                // el token vencido, y el frontend (que cierra la sesión ante un 401) dejaba al usuario
                // trabado viendo errores. Se responde 401/403 con el mismo JSON (ApiError) del resto de la API.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> escribirError(request, response,
                                HttpStatus.UNAUTHORIZED, "Tu sesión no es válida o expiró. Iniciá sesión nuevamente."))
                        .accessDeniedHandler((request, response, e) -> escribirError(request, response,
                                HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este recurso"))
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
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
        return new BCryptPasswordEncoder(); // BCryptPasswordEncoder obligatorio
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
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
        // setAllowedOriginPatterns (no setAllowedOrigins) para poder combinar con allowCredentials(true)
        // y admitir patrones con comodin (Netlify Drop cambia el subdominio en cada arrastre).
        configuration.setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
