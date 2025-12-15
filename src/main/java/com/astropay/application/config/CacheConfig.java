package com.astropay.application.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return createCacheConfiguration(Duration.ofMinutes(5));
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return (builder) -> builder
                .withCacheConfiguration("accounts",
                        createCacheConfiguration(Duration.ofMinutes(10)))
                .withCacheConfiguration("users",
                        createCacheConfiguration(Duration.ofMinutes(10)))
                .withCacheConfiguration("transactions",
                        createCacheConfiguration(Duration.ofMinutes(10)));
    }

    private RedisCacheConfiguration createCacheConfiguration(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                // .disableCachingNullValues()  <-- REMOVED: Allow caching of null values
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(createObjectMapper())));
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        
        // 1. Support for Hibernate types (Lazy Loading, Proxies)
        objectMapper.registerModule(new Hibernate6Module());
        
        // 2. Support for Java 8 dates (LocalDateTime, etc.)
        objectMapper.registerModule(new JavaTimeModule());
        
        // 3. Write dates as ISO-8601 strings instead of numeric timestamps
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 4. Ignore unknown properties in JSON when deserializing (avoids errors if the object changes)
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // 5. Enable polymorphic typing so Redis knows which class to instantiate on return
        objectMapper.activateDefaultTyping(
            objectMapper.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        
        return objectMapper;
    }
}
