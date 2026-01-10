package com.bss.application.dto.response.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.Link;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    @DisplayName("Should verify equals contract")
    void testEqualsContract() {
        UUID idempotencyKey = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        
        TransactionResponse response1 = new TransactionResponse(1L, 10L, 20L, BigDecimal.TEN, "SUCCESS", null, idempotencyKey);
        response1.setCreatedAt(createdAt);

        TransactionResponse response2 = new TransactionResponse(1L, 10L, 20L, BigDecimal.TEN, "SUCCESS", null, idempotencyKey);
        response2.setCreatedAt(createdAt);
        
        // 1. Reflexive (this == o)
        assertTrue(response1.equals(response1));
        
        // 2. Symmetric
        assertTrue(response1.equals(response2));
        assertTrue(response2.equals(response1));
        
        // 3. Null check
        assertFalse(response1.equals(null));
        
        // 4. Different class check
        assertFalse(response1.equals(new Object()));
        
        // 5. Consistent
        assertTrue(response1.equals(response2));
    }

    @Test
    @DisplayName("Should verify equals with super class (RepresentationModel)")
    void testEqualsWithSuperClass() {
        UUID idempotencyKey = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        
        TransactionResponse response1 = new TransactionResponse(1L, 10L, 20L, BigDecimal.TEN, "SUCCESS", null, idempotencyKey);
        response1.setCreatedAt(createdAt);

        TransactionResponse response2 = new TransactionResponse(1L, 10L, 20L, BigDecimal.TEN, "SUCCESS", null, idempotencyKey);
        response2.setCreatedAt(createdAt);
        
        // Add links to response1 but not response2
        response1.add(Link.of("/self"));
        
        // Should be not equal because super.equals checks links
        assertNotEquals(response1, response2);
        
        // Add same link to response2
        response2.add(Link.of("/self"));
        
        // Should be equal now
        assertEquals(response1, response2);
    }

    @Test
    @DisplayName("Should verify hashCode contract")
    void testHashCode() {
        UUID idempotencyKey = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        
        TransactionResponse response1 = new TransactionResponse(1L, 10L, 20L, BigDecimal.TEN, "SUCCESS", null, idempotencyKey);
        response1.setCreatedAt(createdAt);

        TransactionResponse response2 = new TransactionResponse(1L, 10L, 20L, BigDecimal.TEN, "SUCCESS", null, idempotencyKey);
        response2.setCreatedAt(createdAt);

        assertEquals(response1.hashCode(), response2.hashCode());
        
        // Change a field
        response2.setId(2L);
        assertNotEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal with different fields")
    void shouldNotBeEqualWithDifferentFields() {
        UUID idempotencyKey = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        
        TransactionResponse base = new TransactionResponse(1L, 10L, 20L, BigDecimal.TEN, "SUCCESS", null, idempotencyKey);
        base.setCreatedAt(createdAt);

        assertNotEquals(base, new TransactionResponse(2L, 10L, 20L, BigDecimal.TEN, "SUCCESS", null, idempotencyKey));
        assertNotEquals(base, new TransactionResponse(1L, 30L, 20L, BigDecimal.TEN, "SUCCESS", null, idempotencyKey));
        assertNotEquals(base, new TransactionResponse(1L, 10L, 40L, BigDecimal.TEN, "SUCCESS", null, idempotencyKey));
        assertNotEquals(base, new TransactionResponse(1L, 10L, 20L, BigDecimal.ONE, "SUCCESS", null, idempotencyKey));
        assertNotEquals(base, new TransactionResponse(1L, 10L, 20L, BigDecimal.TEN, "FAILED", null, idempotencyKey));
        assertNotEquals(base, new TransactionResponse(1L, 10L, 20L, BigDecimal.TEN, "SUCCESS", "Error", idempotencyKey));
        assertNotEquals(base, new TransactionResponse(1L, 10L, 20L, BigDecimal.TEN, "SUCCESS", null, UUID.randomUUID()));
        
        TransactionResponse differentTime = new TransactionResponse(1L, 10L, 20L, BigDecimal.TEN, "SUCCESS", null, idempotencyKey);
        differentTime.setCreatedAt(createdAt.plusSeconds(1));
        assertNotEquals(base, differentTime);
    }
}
