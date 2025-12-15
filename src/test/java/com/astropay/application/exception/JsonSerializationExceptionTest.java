package com.astropay.application.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonSerializationExceptionTest {

    @Test
    @DisplayName("Should create exception with correct message and cause")
    void shouldCreateExceptionWithMessageAndCause() {
        // Arrange
        String errorMessage = "Error during JSON serialization";
        Throwable cause = new RuntimeException("Underlying serialization error");

        // Act
        JsonSerializationException exception = new JsonSerializationException(errorMessage, cause);

        // Assert
        assertNotNull(exception);
        assertEquals(errorMessage, exception.getMessage(), "The exception message should match the one provided in the constructor.");
        assertEquals(cause, exception.getCause(), "The exception cause should match the one provided in the constructor.");
    }
}
