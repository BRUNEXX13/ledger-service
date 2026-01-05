package com.bss.application.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AsyncConfig.class)
class AsyncConfigTest {

    @Test
    void shouldHaveEnableRetryAnnotation() {
        assertThat(AsyncConfig.class.isAnnotationPresent(EnableRetry.class)).isTrue();
    }

    @Test
    void shouldHaveEnableSchedulingAnnotation() {
        assertThat(AsyncConfig.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
    }

    @Test
    void shouldBeConfigurationClass() {
        assertThat(AsyncConfig.class.isAnnotationPresent(Configuration.class)).isTrue();
    }
}
