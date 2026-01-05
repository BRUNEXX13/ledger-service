package com.bss.application.service.transfer;

import com.bss.application.exception.JsonSerializationException;
import com.bss.domain.model.outbox.OutboxEvent;
import com.bss.domain.model.outbox.OutboxEventRepository;
import com.bss.domain.model.transfer.Transfer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TransferServiceImpl transferService;

    private Transfer validTransfer;

    @BeforeEach
    void setUp() {
        validTransfer = new Transfer(
                1L,
                2L,
                new BigDecimal("100.00"),
                UUID.randomUUID()
        );
    }

    @Test
    @DisplayName("Should create and save an OutboxEvent for a valid transfer")
    void transfer_shouldCreateAndSaveOutboxEvent_forValidTransfer() {
        // Arrange
        ArgumentCaptor<OutboxEvent> outboxEventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);

        // Act
        transferService.transfer(validTransfer);

        // Assert
        verify(outboxEventRepository, times(1)).save(outboxEventCaptor.capture());
        
        OutboxEvent capturedEvent = outboxEventCaptor.getValue();
        assertNotNull(capturedEvent);
        assertEquals("Transfer", capturedEvent.getAggregateType());
        assertEquals("TransferRequested", capturedEvent.getEventType());
        assertEquals(validTransfer.getIdempotencyKey().toString(), capturedEvent.getAggregateId());
        
        // Verify payload content
        String payload = capturedEvent.getPayload();
        assertTrue(payload.contains("\"senderAccountId\":1"));
        assertTrue(payload.contains("\"receiverAccountId\":2"));
        assertTrue(payload.contains("\"amount\":100.00"));
        assertTrue(payload.contains("\"idempotencyKey\":\"" + validTransfer.getIdempotencyKey().toString() + "\""));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException if sender and receiver accounts are the same")
    void transfer_shouldThrowIllegalArgumentException_forSameAccounts() {
        // Arrange
        Transfer invalidTransfer = new Transfer(1L, 1L, new BigDecimal("50.00"), UUID.randomUUID());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transferService.transfer(invalidTransfer);
        });

        assertEquals("Sender and receiver accounts cannot be the same.", exception.getMessage());
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Should throw JsonSerializationException on JSON processing error")
    void transfer_shouldThrowJsonSerializationException_onSerializationError() throws JsonProcessingException {
        // Arrange
        // Configure the spy to throw an exception when trying to serialize
        doThrow(new JsonProcessingException("Serialization error") {}).when(objectMapper).writeValueAsString(any());

        // Act & Assert
        JsonSerializationException exception = assertThrows(JsonSerializationException.class, () -> {
            transferService.transfer(validTransfer);
        });

        assertEquals("Failed to serialize transfer request event to JSON", exception.getMessage());
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof JsonProcessingException);
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }
}
