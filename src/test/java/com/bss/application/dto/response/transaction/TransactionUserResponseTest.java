package com.bss.application.dto.response.transaction;

import com.bss.domain.account.Account;
import com.bss.domain.transaction.Transaction;
import com.bss.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionUserResponseTest {

    @Test
    @DisplayName("Should create instance using no-args constructor")
    void shouldCreateInstanceUsingNoArgsConstructor() {
        TransactionUserResponse response = new TransactionUserResponse();
        assertNotNull(response);
        assertNull(response.getTransactionId());
        assertNull(response.getSenderName());
    }

    @Test
    @DisplayName("Should create instance using all-args constructor")
    void shouldCreateInstanceUsingAllArgsConstructor() {
        UUID key = UUID.randomUUID();
        Long id = 1L;
        String name = "John";
        String email = "john@test.com";
        String doc = "123";
        LocalDateTime date = LocalDateTime.now();

        TransactionUserResponse response = new TransactionUserResponse(key, id, name, email, doc, date);

        assertEquals(key, response.getTransactionIdempotencyKey());
        assertEquals(id, response.getTransactionId());
        assertEquals(name, response.getSenderName());
        assertEquals(email, response.getSenderEmail());
        assertEquals(doc, response.getSenderDocument());
        assertEquals(date, response.getTransactionDate());
    }

    @Test
    @DisplayName("Should create instance from Transaction entity")
    void shouldCreateInstanceFromEntity() {
        // Arrange
        User user = mock(User.class);
        when(user.getName()).thenReturn("John Doe");
        when(user.getEmail()).thenReturn("john@test.com");
        when(user.getDocument()).thenReturn("12345678900");

        Account sender = mock(Account.class);
        when(sender.getUser()).thenReturn(user);

        Account receiver = mock(Account.class);

        UUID key = UUID.randomUUID();
        Transaction transaction = new Transaction(sender, receiver, BigDecimal.TEN, key);
        ReflectionTestUtils.setField(transaction, "id", 100L);
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(transaction, "createdAt", now);

        // Act
        TransactionUserResponse response = new TransactionUserResponse(transaction);

        // Assert
        assertEquals(key, response.getTransactionIdempotencyKey());
        assertEquals(100L, response.getTransactionId());
        assertEquals("John Doe", response.getSenderName());
        assertEquals("john@test.com", response.getSenderEmail());
        assertEquals("12345678900", response.getSenderDocument());
        assertEquals(now, response.getTransactionDate());
    }
}
