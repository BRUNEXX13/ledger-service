package com.bss.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CacheConfigTest {

    @Test
    @DisplayName("Should ignore HATEOAS links during serialization")
    void shouldIgnoreHateoasLinks() throws Exception {
        // Arrange
        CacheConfig cacheConfig = new CacheConfig();
        ObjectMapper objectMapper = ReflectionTestUtils.invokeMethod(cacheConfig, "createObjectMapper");
        assertNotNull(objectMapper);

        TestModel model = new TestModel();
        model.add(Link.of("http://localhost/api/test", "self"));

        // Act
        String json = objectMapper.writeValueAsString(model);

        // Assert
        assertFalse(json.contains("links"), "JSON should not contain 'links' field");
        assertFalse(json.contains("http://localhost/api/test"), "JSON should not contain link URL");
    }

    // Helper class extending RepresentationModel to test mixin
    static class TestModel extends RepresentationModel<TestModel> {
        private String name = "test";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
