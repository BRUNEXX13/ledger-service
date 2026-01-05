package com.bss.application.controller.transfer.mapper;

import com.bss.application.dto.request.transfer.TransferRequest;
import com.bss.domain.model.transfer.Transfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransferMapperTest {

    private final TransferMapper mapper = new TransferMapper();

    @Test
    @DisplayName("Should map TransferRequest to Transfer domain object")
    void shouldMapRequestToDomain() {
        // Arrange
        UUID idempotencyKey = UUID.randomUUID();
        TransferRequest request = new TransferRequest();
        request.setSenderAccountId(1L);
        request.setReceiverAccountId(2L);
        request.setAmount(new BigDecimal("99.99"));
        request.setIdempotencyKey(idempotencyKey);

        // Act
        Transfer transfer = mapper.toDomain(request);

        // Assert
        assertNotNull(transfer);
        assertEquals(1L, transfer.getSenderAccountId());
        assertEquals(2L, transfer.getReceiverAccountId());
        assertEquals(0, new BigDecimal("99.99").compareTo(transfer.getAmount()));
        assertEquals(idempotencyKey, transfer.getIdempotencyKey());
    }

    @Test
    @DisplayName("Should return null when mapping a null TransferRequest")
    void shouldReturnNullForNullRequest() {
        assertNull(mapper.toDomain(null));
    }
}
