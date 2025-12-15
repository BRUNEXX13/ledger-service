package com.astropay.application.event.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AccountCreatedEventTest {

    @Test
    @DisplayName("Should create event with all-args constructor and getters should work")
    void shouldCreateEventWithAllArgsConstructor() {
        // Arrange
        Long accountId = 1L;
        Long userId = 2L;
        String userName = "John Doe";
        String userEmail = "john.doe@example.com";
        LocalDateTime createdAt = LocalDateTime.now();

        // Act
        AccountCreatedEvent event = new AccountCreatedEvent(accountId, userId, userName, userEmail, createdAt);

        // Assert
        assertNotNull(event);
        assertEquals(accountId, event.getAccountId());
        assertEquals(userId, event.getUserId());
        assertEquals(userName, event.getUserName());
        assertEquals(userEmail, event.getUserEmail());
        assertEquals(createdAt, event.getCreatedAt());
    }

    @Test
    @DisplayName("Should create event with no-args constructor and setters/getters should work")
    void shouldCreateEventWithNoArgsConstructorAndSetters() {
        // Arrange
        Long accountId = 1L;
        Long userId = 2L;
        String userName = "John Doe";
        String userEmail = "john.doe@example.com";
        LocalDateTime createdAt = LocalDateTime.now();

        // Act
        AccountCreatedEvent event = new AccountCreatedEvent();
        event.setAccountId(accountId);
        event.setUserId(userId);
        event.setUserName(userName);
        event.setUserEmail(userEmail);
        event.setCreatedAt(createdAt);

        // Assert
        assertNotNull(event);
        assertEquals(accountId, event.getAccountId());
        assertEquals(userId, event.getUserId());
        assertEquals(userName, event.getUserName());
        assertEquals(userEmail, event.getUserEmail());
        assertEquals(createdAt, event.getCreatedAt());
    }
}
