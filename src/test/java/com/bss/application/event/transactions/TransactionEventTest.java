package com.bss.application.event.transactions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TransactionEventTest {

    @Test
    @DisplayName("Should create event with all-args constructor and getters should work")
    void shouldCreateEventWithAllArgsConstructor() {
        // Arrange
        Long transactionId = 1L;
        Long senderAccountId = 10L;
        Long receiverAccountId = 20L;
        BigDecimal amount = new BigDecimal("100.50");
        Instant timestamp = Instant.now();
        UUID idempotencyKey = UUID.randomUUID();

        // Act
        TransactionEvent event = new TransactionEvent(transactionId, senderAccountId, receiverAccountId, amount, timestamp, idempotencyKey);

        // Assert
        assertNotNull(event);
        assertEquals(transactionId, event.getTransactionId());
        assertEquals(senderAccountId, event.getSenderAccountId());
        assertEquals(receiverAccountId, event.getReceiverAccountId());
        assertEquals(amount, event.getAmount());
        assertEquals(timestamp, event.getTimestamp());
        assertEquals(idempotencyKey, event.getIdempotencyKey());
    }

    @Test
    @DisplayName("Should create event with no-args constructor and setters/getters should work")
    void shouldCreateEventWithNoArgsConstructorAndSetters() {
        // Arrange
        Long transactionId = 1L;
        Long senderAccountId = 10L;
        Long receiverAccountId = 20L;
        BigDecimal amount = new BigDecimal("100.50");
        Instant timestamp = Instant.now();
        UUID idempotencyKey = UUID.randomUUID();

        // Act
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(transactionId);
        event.setSenderAccountId(senderAccountId);
        event.setReceiverAccountId(receiverAccountId);
        event.setAmount(amount);
        event.setTimestamp(timestamp);
        event.setIdempotencyKey(idempotencyKey);

        // Assert
        assertNotNull(event);
        assertEquals(transactionId, event.getTransactionId());
        assertEquals(senderAccountId, event.getSenderAccountId());
        assertEquals(receiverAccountId, event.getReceiverAccountId());
        assertEquals(amount, event.getAmount());
        assertEquals(timestamp, event.getTimestamp());
        assertEquals(idempotencyKey, event.getIdempotencyKey());
    }
}
