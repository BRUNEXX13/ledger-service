//package com.bss;
//
//import org.junit.jupiter.api.Test;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.testcontainers.containers.GenericContainer;
//import org.testcontainers.containers.KafkaContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//import org.testcontainers.utility.DockerImageName;
//
//import org.testcontainers.containers.PostgreSQLContainer;
//
//import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
//
//@SpringBootTest
//@ActiveProfiles("test")
//@Testcontainers
//class LedgerServiceApplicationTest {
//
//    @Container
//    @ServiceConnection
//    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
//
//    @Container
//    @ServiceConnection
//    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.0"))
//            .withExposedPorts(6379);
//
//    @Container
//    @ServiceConnection
//    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
//
//    @DynamicPropertySource
//    static void configureProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.docker.compose.enabled", () -> "false");
//        registry.add("spring.flyway.enabled", () -> "true");
//        // Explicitly set the driver class name for the test container
//        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
//
//        // Ensure Hibernate uses the correct dialect and doesn't try to use a default that might fail
//        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
//    }
//
//    @Test
//    void contextLoads() {
//        // This test checks if the Spring Boot application context loads without errors.
//    }
//}
