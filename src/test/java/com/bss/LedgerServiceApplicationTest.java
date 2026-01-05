package com.bss;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LedgerServiceApplicationTest {

    @Test
    void contextLoads() {
        // This test checks if the Spring Boot application context loads without errors.
        // If there are configuration issues, missing beans, or conflicts, this test will fail.
    }
}
