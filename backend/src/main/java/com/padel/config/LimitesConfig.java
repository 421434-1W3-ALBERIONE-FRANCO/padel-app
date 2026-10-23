package com.padel.config;

import com.padel.security.LimitadorDePedidos;
import com.padel.security.LimitadorDePedidosRedis;
import com.padel.security.LimitadorEnMemoria;
import com.padel.security.RevocacionTokens;
import com.padel.security.RevocacionTokensEnMemoria;
import com.padel.security.RevocacionTokensRedis;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Elige dónde se guardan los contadores de límites y los tokens revocados: Redis (por defecto) o memoria. */
@Configuration
public class LimitesConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.limites", name = "usar-redis", havingValue = "true", matchIfMissing = true)
    public LimitadorDePedidos limitadorDePedidosRedis(StringRedisTemplate redis) {
        return new LimitadorDePedidosRedis(redis);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.limites", name = "usar-redis", havingValue = "false")
    public LimitadorDePedidos limitadorDePedidosEnMemoria() {
        return new LimitadorEnMemoria();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.limites", name = "usar-redis", havingValue = "true", matchIfMissing = true)
    public RevocacionTokens revocacionTokensRedis(StringRedisTemplate redis) {
        return new RevocacionTokensRedis(redis);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.limites", name = "usar-redis", havingValue = "false")
    public RevocacionTokens revocacionTokensEnMemoria() {
        return new RevocacionTokensEnMemoria();
    }
}
