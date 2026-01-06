package com.bss.infrastructure.redis;

import com.bss.application.dto.response.account.AccountResponse;
import com.bss.domain.account.AccountStatus;
import com.bss.infrastructure.redis.CacheConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {CacheConfig.class, RedisAutoConfiguration.class})
@Testcontainers
class RedisCacheIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.0"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Redis properties
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // Disable JPA and Flyway for this slice test
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.autoconfigure.exclude", () -> "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration");
    }

    @Autowired
    private CacheManager cacheManager;

    @Test
    void testCacheSerialization() {
        // 1. Prepare Data
        LocalDateTime now = LocalDateTime.now();
        AccountResponse originalResponse = new AccountResponse(
                1L, 100L, BigDecimal.valueOf(500.00), AccountStatus.ACTIVE, now, now
        );

        // 2. Put in Cache
        Cache cache = cacheManager.getCache("accounts");
        assertThat(cache).isNotNull();
        cache.put(1L, originalResponse);

        // 3. Get from Cache
        AccountResponse cachedResponse = cache.get(1L, AccountResponse.class);

        // 4. Verify
        assertThat(cachedResponse).isNotNull();
        assertThat(cachedResponse.getId()).isEqualTo(originalResponse.getId());
        assertThat(cachedResponse.getBalance()).isEqualByComparingTo(originalResponse.getBalance());
        assertThat(cachedResponse.getStatus()).isEqualTo(originalResponse.getStatus());
        assertThat(cachedResponse.getCreatedAt()).isEqualToIgnoringNanos(originalResponse.getCreatedAt());
    }
}
