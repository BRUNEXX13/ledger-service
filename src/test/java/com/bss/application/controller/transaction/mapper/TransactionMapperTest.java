package com.bss.application.controller.transaction.mapper;

import com.bss.application.dto.response.transaction.TransactionResponse;
import com.bss.domain.account.Account;
import com.bss.domain.transaction.Transaction;
import com.bss.domain.transaction.TransactionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionMapperTest {

    private final TransactionMapper mapper = new TransactionMapper();

    @Test
    @DisplayName("Should map Transaction entity to TransactionResponse DTO correctly")
    void shouldMapEntityToDto() {
        // Arrange
        Account sender = mock(Account.class);
        when(sender.getId()).thenReturn(1L);

        Account receiver = mock(Account.class);
        when(receiver.getId()).thenReturn(2L);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getId()).thenReturn(100L);
        when(transaction.getSender()).thenReturn(sender);
        when(transaction.getReceiver()).thenReturn(receiver);
        when(transaction.getAmount()).thenReturn(BigDecimal.valueOf(123.45));
        when(transaction.getStatus()).thenReturn(TransactionStatus.SUCCESS);
        when(transaction.getFailureReason()).thenReturn(null);
        when(transaction.getIdempotencyKey()).thenReturn(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

        // Act
        TransactionResponse response = mapper.toTransactionResponse(transaction);

        // Assert
        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(1L, response.getSenderAccountId());
        assertEquals(2L, response.getReceiverAccountId());
        assertEquals(0, BigDecimal.valueOf(123.45).compareTo(response.getAmount()));
        assertEquals("SUCCESS", response.getStatus());
        assertNull(response.getFailureReason());
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), response.getIdempotencyKey());
    }

    @Test
    @DisplayName("Should return null when mapping a null Transaction")
    void shouldReturnNullForNullEntity() {
        assertNull(mapper.toTransactionResponse(null));
    }
}
