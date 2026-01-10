package com.bss.application.controller.transfer.mapper;

import com.bss.application.dto.request.transfer.TransferRequest;
import com.bss.domain.transfer.Transfer;
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
        TransferRequest request = new TransferRequest();
        request.setSenderAccountId(1L);
        request.setReceiverAccountId(2L);
        request.setAmount(new BigDecimal("100.00"));
        UUID key = UUID.randomUUID();
        request.setIdempotencyKey(key);

        // Act
        Transfer transfer = mapper.toDomain(request);

        // Assert
        assertNotNull(transfer);
        assertEquals(1L, transfer.getSenderAccountId());
        assertEquals(2L, transfer.getReceiverAccountId());
        assertEquals(new BigDecimal("100.00"), transfer.getAmount());
        assertEquals(key, transfer.getIdempotencyKey());
    }

    @Test
    @DisplayName("Should return null when request is null")
    void shouldReturnNullWhenRequestIsNull() {
        assertNull(mapper.toDomain(null));
    }
}
