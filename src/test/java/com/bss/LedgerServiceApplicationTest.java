package com.bss;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.testcontainers.containers.PostgreSQLContainer;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LedgerServiceApplicationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.0"))
            .withExposedPorts(6379);

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.docker.compose.enabled", () -> "false");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        
        // Force clear any exclusions that might be leaking from other tests or environment
        registry.add("spring.autoconfigure.exclude", () -> "");
    }

    @Test
    void contextLoads() {
        // This test checks if the Spring Boot application context loads without errors.
    }
}
