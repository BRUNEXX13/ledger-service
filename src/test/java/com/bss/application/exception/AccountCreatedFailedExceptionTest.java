package com.bss.application.exception;

import com.bss.application.exception.AccountCreatedFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AccountCreatedFailedExceptionTest {

    @Test
    @DisplayName("Should create exception with message and cause")
    void shouldCreateExceptionWithMessageAndCause() {
        // Arrange
        String errorMessage = "Failed to create account";
        Throwable cause = new RuntimeException("Underlying database error");

        // Act
        AccountCreatedFailedException exception = new AccountCreatedFailedException(errorMessage, cause);

        // Assert
        assertNotNull(exception);
        assertEquals(errorMessage, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
