package com.bss.application.exception;

import com.bss.application.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResourceNotFoundExceptionTest {

    @Test
    @DisplayName("Should create exception with the correct message")
    void shouldCreateExceptionWithMessage() {
        // Arrange
        String errorMessage = "Resource with ID 123 not found.";

        // Act
        ResourceNotFoundException exception = new ResourceNotFoundException(errorMessage);

        // Assert
        assertNotNull(exception, "The exception object should not be null.");
        assertEquals(errorMessage, exception.getMessage(), "The exception message should match the one provided in the constructor.");
        assertNull(exception.getCause(), "The cause should be null as it is not set in the constructor.");
    }
}
