package com.bss.application.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncConfigTest {

    @Test
    @DisplayName("Should have @Configuration annotation")
    void shouldHaveConfigurationAnnotation() {
        assertTrue(AsyncConfig.class.isAnnotationPresent(Configuration.class));
    }

    @Test
    @DisplayName("Should have @EnableRetry annotation")
    void shouldHaveEnableRetryAnnotation() {
        assertTrue(AsyncConfig.class.isAnnotationPresent(EnableRetry.class));
    }

    @Test
    @DisplayName("Should have @EnableScheduling annotation")
    void shouldHaveEnableSchedulingAnnotation() {
        assertTrue(AsyncConfig.class.isAnnotationPresent(EnableScheduling.class));
    }

    @Test
    @DisplayName("Should be able to instantiate AsyncConfig")
    void shouldInstantiate() {
        AsyncConfig config = new AsyncConfig();
        assertNotNull(config);
    }
}
