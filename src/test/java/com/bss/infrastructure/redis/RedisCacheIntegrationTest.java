package com.bss.infrastructure.redis;

import com.bss.application.dto.response.account.AccountResponse;
import com.bss.domain.account.AccountStatus;
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
        // REMOVED: registry.add("spring.autoconfigure.exclude", ...);
        // This property was leaking into other tests via System properties or shared context logic in some environments,
        // although @DynamicPropertySource should be local.
        // However, the error log showed exclusions:
        // Exclusions:
        // -----------
        //    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
        //    org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
        //    org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
        //
        // This strongly suggests that this configuration is affecting the main application test.
        // To fix this in a robust way for the whole suite, we should ensure tests don't pollute the global state.
        // But for now, let's remove the explicit exclusion here if it's not strictly needed or rename the property key if it's being picked up globally.
        // Actually, for a slice test like this using @ContextConfiguration, we don't need to exclude auto-configurations via property if we are explicit about classes.
        // But RedisAutoConfiguration might trigger others? No.
        // The issue is likely that "spring.autoconfigure.exclude" is a special Boot property.
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
