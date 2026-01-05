package com.bss.infrastructure.config;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class ResilienceConfig {

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        // 1. Criar a configuração compartilhada
        RateLimiterConfig sharedConfig = RateLimiterConfig.custom()
                .limitForPeriod(50000) // 50.000 requisições
                .limitRefreshPeriod(Duration.ofSeconds(1)) // por segundo
                .timeoutDuration(Duration.ZERO) // Não esperar
                .build();

        // 2. Criar um mapa de configurações
        Map<String, RateLimiterConfig> configs = new HashMap<>();
        // A chave "default" aqui é permitida, pois é usada para construir o registro
        configs.put("default", sharedConfig);

        // 3. Criar o registro usando o mapa
        return RateLimiterRegistry.of(configs);
    }
}
