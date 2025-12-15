package com.astropay.application.event.account;

import com.astropay.application.exception.AccountCreatedFailedException;
import com.astropay.application.service.notification.EmailService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountCreatedEventListenerTest {

    @Mock
    private EmailService emailService;

    private AccountCreatedEventListener eventListener;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());
        eventListener = new AccountCreatedEventListener(emailService, objectMapper);
    }

    @Test
    @DisplayName("Should handle account created event and send email notification")
    void shouldHandleAccountCreatedEvent() throws JsonProcessingException {
        // Arrange
        AccountCreatedEvent event = new AccountCreatedEvent(
                100L,
                1L,
                "John Doe",
                "john.doe@example.com",
                LocalDateTime.now()
        );
        String jsonPayload = objectMapper.writeValueAsString(event);

        // Act
        eventListener.handleAccountCreatedEvent(jsonPayload);

        // Assert
        ArgumentCaptor<String> toCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

        verify(emailService).sendTransactionNotification(toCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture());

        assertEquals(event.getUserEmail(), toCaptor.getValue());
        assertEquals("Welcome to Our Bank!", subjectCaptor.getValue());
        assertTrue(bodyCaptor.getValue().contains("Hello, John Doe!"));
        assertTrue(bodyCaptor.getValue().contains("Your account ID is 100."));
    }

    @Test
    @DisplayName("Should throw AccountCreatedFailedException when email service fails")
    void shouldThrowExceptionWhenEmailServiceFails() throws JsonProcessingException {
        // Arrange
        AccountCreatedEvent event = new AccountCreatedEvent(
                100L,
                1L,
                "John Doe",
                "john.doe@example.com",
                LocalDateTime.now()
        );
        String jsonPayload = objectMapper.writeValueAsString(event);

        // Mock the email service to throw an exception
        doThrow(new RuntimeException("Email server is down")).when(emailService)
                .sendTransactionNotification(anyString(), anyString(), anyString());

        // Act & Assert
        AccountCreatedFailedException exception = assertThrows(
                AccountCreatedFailedException.class,
                () -> eventListener.handleAccountCreatedEvent(jsonPayload)
        );

        // Verify the exception message contains contextual information
        assertTrue(exception.getMessage().contains("Failed to process account creation event from payload: " + jsonPayload));
    }

    @Test
    @DisplayName("Should throw AccountCreatedFailedException for invalid JSON payload")
    void shouldThrowExceptionForInvalidPayload() {
        // Arrange
        String invalidPayload = "{\"accountId\":100, \"invalidJson\"}";

        // Act & Assert
        AccountCreatedFailedException exception = assertThrows(
                AccountCreatedFailedException.class,
                () -> eventListener.handleAccountCreatedEvent(invalidPayload)
        );

        assertTrue(exception.getMessage().contains("Failed to process account creation event from payload: " + invalidPayload));
        verify(emailService, never()).sendTransactionNotification(anyString(), anyString(), anyString());
    }
}
