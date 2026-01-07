package com.bss.application.service.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class EmailServiceImplTest {

    private final EmailServiceImpl emailService = new EmailServiceImpl();

    @Test
    @DisplayName("Should execute send email logic without throwing exceptions")
    void shouldSendEmailWithoutErrors() {
        // Arrange
        String to = "test@example.com";
        String subject = "Test Subject";
        String body = "Test Body";

        // Act & Assert
        // We are just verifying that the simulated email sending
        // does not throw any unexpected exceptions.
        assertDoesNotThrow(() -> emailService.sendTransactionNotification(to, subject, body));
    }
}
