package com.astropay.infrastructure.redis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Módulos essenciais
        objectMapper.registerModule(new JavaTimeModule());
        
        // REMOVIDO: Jackson2HalModule
        // Este módulo causa conflitos de serialização com o Redis (HalLinkListSerializer has no default constructor).
        // Sem ele, os objetos serão cacheados como JSON puro, o que é suficiente para leitura e evita o erro.
        
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // Ativa a tipagem padrão do GenericJackson2JsonRedisSerializer de forma segura
        // Isso permite que o Redis saiba qual classe instanciar na volta
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisSerializationContext.SerializationPair<Object> serializationPair = RedisSerializationContext.SerializationPair.fromSerializer(serializer);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeValuesWith(serializationPair);

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration("transactions",
                defaultConfig.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration("users",
                defaultConfig.entryTtl(Duration.ofMinutes(10)))
            .withCacheConfiguration("accounts",
                defaultConfig.entryTtl(Duration.ofMinutes(10)))
            .build();
    }
}
