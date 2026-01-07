package com.bss.application.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpaConfigTest {

    @Test
    @DisplayName("Should have @Configuration annotation")
    void shouldHaveConfigurationAnnotation() {
        assertTrue(JpaConfig.class.isAnnotationPresent(Configuration.class));
    }

    @Test
    @DisplayName("Should have @EnableJpaAuditing annotation")
    void shouldHaveEnableJpaAuditingAnnotation() {
        assertTrue(JpaConfig.class.isAnnotationPresent(EnableJpaAuditing.class));
    }

    @Test
    @DisplayName("Should have @EnableJpaRepositories annotation with correct base package")
    void shouldHaveEnableJpaRepositoriesAnnotation() {
        assertTrue(JpaConfig.class.isAnnotationPresent(EnableJpaRepositories.class));
        
        EnableJpaRepositories annotation = JpaConfig.class.getAnnotation(EnableJpaRepositories.class);
        assertEquals("com.bss.domain", annotation.basePackages()[0]);
    }

    @Test
    @DisplayName("Should be able to instantiate JpaConfig")
    void shouldInstantiate() {
        JpaConfig config = new JpaConfig();
        assertNotNull(config);
    }
}
