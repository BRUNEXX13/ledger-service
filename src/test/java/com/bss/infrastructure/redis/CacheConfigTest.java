package com.bss.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig();

    @Test
    @DisplayName("Should create default cache configuration with 5 minutes TTL")
    void shouldCreateDefaultCacheConfiguration() {
        RedisCacheConfiguration config = cacheConfig.cacheConfiguration();

        assertNotNull(config);
        assertEquals(Duration.ofMinutes(5), config.getTtl());
        assertNotNull(config.getValueSerializationPair());
    }

    @Test
    @DisplayName("Should create CacheManager with specific configurations")
    void shouldCreateCacheManagerWithSpecificConfigs() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        
        CacheManager cacheManager = cacheConfig.cacheManager(connectionFactory);

        assertNotNull(cacheManager);
        assertTrue(cacheManager instanceof RedisCacheManager);
        
        RedisCacheManager redisCacheManager = (RedisCacheManager) cacheManager;
        
        // Access private field to verify initial configurations
        @SuppressWarnings("unchecked")
        Map<String, RedisCacheConfiguration> initialCacheConfiguration = 
            (Map<String, RedisCacheConfiguration>) ReflectionTestUtils.getField(redisCacheManager, "initialCacheConfiguration");
        
        assertNotNull(initialCacheConfiguration);
        assertTrue(initialCacheConfiguration.containsKey("accounts"));
        assertTrue(initialCacheConfiguration.containsKey("users"));
        assertTrue(initialCacheConfiguration.containsKey("transactions"));
        
        assertEquals(Duration.ofMinutes(10), initialCacheConfiguration.get("accounts").getTtl());
        assertEquals(Duration.ofMinutes(10), initialCacheConfiguration.get("users").getTtl());
        assertEquals(Duration.ofMinutes(10), initialCacheConfiguration.get("transactions").getTtl());
    }

    @Test
    @DisplayName("Should configure ObjectMapper correctly")
    void shouldConfigureObjectMapperCorrectly() throws ClassNotFoundException {
        // Invoke private method to get the ObjectMapper
        ObjectMapper mapper = ReflectionTestUtils.invokeMethod(cacheConfig, "createObjectMapper");
        
        assertNotNull(mapper);
        
        Set<Object> moduleIds = mapper.getRegisteredModuleIds();
        String moduleNames = moduleIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
        
        // Verify modules are registered (Hibernate6, JavaTime)
        // Hibernate6Module ID might be different depending on version, usually "com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module"
        boolean hasHibernate = moduleNames.contains("Hibernate6Module") || moduleNames.contains("hibernate6");
        boolean hasJavaTime = moduleNames.contains("JavaTimeModule") || moduleNames.contains("jsr310");
        
        assertTrue(hasHibernate, "Expected Hibernate6Module to be registered. Registered modules: " + moduleNames);
        assertTrue(hasJavaTime, "Expected JavaTimeModule to be registered. Registered modules: " + moduleNames);
        
        // Verify MixIn
        Class<?> mixin = mapper.findMixInClassFor(org.springframework.hateoas.RepresentationModel.class);
        assertNotNull(mixin, "MixIn for RepresentationModel should not be null");
        
        assertTrue(mixin.getName().contains("RepresentationModelMixin"), 
            "Expected MixIn name to contain 'RepresentationModelMixin', but was: " + mixin.getName());
    }
}
