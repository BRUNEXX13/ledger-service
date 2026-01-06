package com.bss.application.config;

import com.bss.application.config.JpaConfig;
import com.bss.domain.account.AccountRepository;
import com.bss.domain.transaction.TransactionRepository;
import com.bss.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JpaConfigTest {

    // This internal configuration replaces LedgerServiceApplication for this test,
    // avoiding the global ComponentScan that would bring Controllers and break the JPA test.
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.bss.domain") // Required to find JPA entities
    static class TestConfig {
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private AccountRepository accountRepository;

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private TransactionRepository transactionRepository;

    @Test
    void shouldEnableJpaRepositories() {
        assertThat(accountRepository).isNotNull();
        assertThat(userRepository).isNotNull();
        assertThat(transactionRepository).isNotNull();
    }

    @Test
    void shouldEnableJpaAuditing() {
        assertThat(JpaConfig.class.isAnnotationPresent(EnableJpaAuditing.class)).isTrue();
    }
}
