package com.padel.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String TOPICO_DISPONIBILIDAD = "/topic/disponibilidad";

    // Mismos orígenes que CORS: antes se aceptaba cualquiera ("*").
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origenes = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toArray(String[]::new);
        registry.addEndpoint("/ws").setAllowedOriginPatterns(origenes).withSockJS();
        registry.addEndpoint("/ws").setAllowedOriginPatterns(origenes);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // El canal solo baja avisos cortos: mensajes y buffers chicos acotan el consumo de memoria por conexión.
        registration.setMessageSizeLimit(8 * 1024);
        registration.setSendBufferSizeLimit(64 * 1024);
        registration.setSendTimeLimit(10_000);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // El handshake es público: el canal es solo de difusión servidor -> clientes. Se descartan los SEND de
        // los clientes y las suscripciones a cualquier otro destino que no sea el de disponibilidad.
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                if (StompCommand.SEND.equals(accessor.getCommand())) {
                    return null;
                }
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand()) && !TOPICO_DISPONIBILIDAD.equals(accessor.getDestination())) {
                    return null;
                }
                return message;
            }
        });
    }
}
