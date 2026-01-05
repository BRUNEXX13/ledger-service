package com.bss.infrastructure.redis;

import com.bss.LedgerServiceApplication;
import com.bss.application.dto.response.account.AccountResponse;
import com.bss.domain.account.AccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = LedgerServiceApplication.class)
@Testcontainers
@ActiveProfiles("test")
class RedisCacheIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.0"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private CacheManager cacheManager;

    @Test
    void testCacheSerialization() {
        // 1. Preparar Dados
        LocalDateTime now = LocalDateTime.now();
        AccountResponse originalResponse = new AccountResponse(
                1L, 100L, BigDecimal.valueOf(500.00), AccountStatus.ACTIVE, now, now
        );

        // 2. Colocar no Cache
        Cache cache = cacheManager.getCache("accounts");
        assertThat(cache).isNotNull();
        cache.put(1L, originalResponse);

        // 3. Recuperar do Cache
        AccountResponse cachedResponse = cache.get(1L, AccountResponse.class);

        // 4. Verificar
        assertThat(cachedResponse).isNotNull();
        assertThat(cachedResponse.getId()).isEqualTo(originalResponse.getId());
        assertThat(cachedResponse.getBalance()).isEqualByComparingTo(originalResponse.getBalance());
        assertThat(cachedResponse.getStatus()).isEqualTo(originalResponse.getStatus());
        // Verificar se a data foi serializada/deserializada corretamente
        assertThat(cachedResponse.getCreatedAt()).isEqualToIgnoringNanos(originalResponse.getCreatedAt());
    }
}
