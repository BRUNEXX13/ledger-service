package com.astropay.infrastructure.config;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(50000) // 50.000 requisições
                .limitRefreshPeriod(Duration.ofSeconds(1)) // por segundo
                .timeoutDuration(Duration.ZERO) // Não esperar
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.ofDefaults();
        registry.addConfiguration("transfers", config); // Garante para a instância "transfers"
        registry.addConfiguration("default", config);   // Aplica como padrão também

        return registry;
    }
}
