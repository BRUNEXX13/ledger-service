package com.bss.application.dto.response.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TransactionResponseTest {

    @Test
    @DisplayName("Should create TransactionResponse with all fields")
    void shouldCreateTransactionResponseWithAllFields() {
        Long id = 1L;
        Long senderId = 10L;
        Long receiverId = 20L;
        BigDecimal amount = new BigDecimal("100.00");
        String status = "SUCCESS";
        String failureReason = null;
        UUID idempotencyKey = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        TransactionResponse response = new TransactionResponse(id, senderId, receiverId, amount, status, failureReason, idempotencyKey);
        response.setCreatedAt(createdAt);

        assertEquals(id, response.getId());
        assertEquals(senderId, response.getSenderAccountId());
        assertEquals(receiverId, response.getReceiverAccountId());
        assertEquals(amount, response.getAmount());
        assertEquals(status, response.getStatus());
        assertNull(response.getFailureReason());
        assertEquals(idempotencyKey, response.getIdempotencyKey());
        assertEquals(createdAt, response.getCreatedAt());
    }

    @Test
    @DisplayName("Should create empty TransactionResponse and set fields")
    void shouldCreateEmptyTransactionResponseAndSetFields() {
        TransactionResponse response = new TransactionResponse();
        
        Long id = 2L;
        response.setId(id);
        response.setSenderAccountId(30L);
        response.setReceiverAccountId(40L);
        response.setAmount(BigDecimal.TEN);
        response.setStatus("FAILED");
        response.setFailureReason("Insufficient funds");
        UUID uuid = UUID.randomUUID();
        response.setIdempotencyKey(uuid);
        LocalDateTime now = LocalDateTime.now();
        response.setCreatedAt(now);

        assertEquals(id, response.getId());
        assertEquals(30L, response.getSenderAccountId());
        assertEquals(40L, response.getReceiverAccountId());
        assertEquals(BigDecimal.TEN, response.getAmount());
        assertEquals("FAILED", response.getStatus());
        assertEquals("Insufficient funds", response.getFailureReason());
        assertEquals(uuid, response.getIdempotencyKey());
        assertEquals(now, response.getCreatedAt());
    }
}
