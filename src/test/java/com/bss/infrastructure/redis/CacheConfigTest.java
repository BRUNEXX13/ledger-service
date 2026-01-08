package com.bss.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.RepresentationModel;

import java.nio.ByteBuffer;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig();

    @Test
    @DisplayName("Should ignore HATEOAS links during serialization")
    void shouldIgnoreHateoasLinks() {
        // Arrange
        RedisCacheConfiguration config = cacheConfig.cacheConfiguration();
        RedisSerializationContext.SerializationPair<Object> serializationPair = config.getValueSerializationPair();
        
        // Create a sample HATEOAS model with links
        TestModel model = new TestModel("test-content");
        model.add(Link.of("http://localhost/self", "self"));

        // Act
        // Use the SerializationPair directly to write (serialize) the object
        ByteBuffer buffer = serializationPair.write(model);
        
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String json = new String(bytes);

        // Assert
        assertThat(json).contains("test-content");
        // Verify links are NOT present in the JSON
        assertThat(json).doesNotContain("links");
        assertThat(json).doesNotContain("_links");
        assertThat(json).doesNotContain("http://localhost/self");
        
        // Verify type info is present (due to activateDefaultTyping)
        assertThat(json).contains("@class");
        assertThat(json).contains("com.bss.infrastructure.redis.CacheConfigTest$TestModel");
    }

    @Test
    @DisplayName("Should deserialize object correctly")
    void shouldDeserializeObject() {
        // Arrange
        RedisCacheConfiguration config = cacheConfig.cacheConfiguration();
        RedisSerializationContext.SerializationPair<Object> serializationPair = config.getValueSerializationPair();
        
        TestModel original = new TestModel("round-trip-content");
        
        // Act
        ByteBuffer buffer = serializationPair.write(original);
        Object deserialized = serializationPair.read(buffer);

        // Assert
        assertThat(deserialized).isInstanceOf(TestModel.class);
        TestModel result = (TestModel) deserialized;
        assertThat(result.getContent()).isEqualTo("round-trip-content");
    }

    @Test
    @DisplayName("Should configure default TTL correctly")
    void shouldConfigureDefaultTtl() {
        RedisCacheConfiguration config = cacheConfig.cacheConfiguration();
        assertThat(config.getTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    // Helper class extending RepresentationModel to simulate a DTO
    // Must be static for Jackson to instantiate it without an enclosing instance
    static class TestModel extends RepresentationModel<TestModel> {
        private String content;

        public TestModel() {} // For Jackson

        public TestModel(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }
}
