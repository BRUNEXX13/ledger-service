package com.bss.application.service.transfer;

import com.bss.application.event.transactions.TransferRequestedEvent;
import com.bss.application.exception.JsonSerializationException;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.bss.domain.transfer.Transfer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

    @Mock
    private ObjectMapper objectMapper;

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
    @DisplayName("Should save OutboxEvent for a valid transfer")
    void transfer_shouldSaveOutboxEvent_forValidTransfer() throws JsonProcessingException {
        // Arrange
        String expectedJson = "{\"senderAccountId\":1,\"receiverAccountId\":2,\"amount\":100.00}";
        when(objectMapper.writeValueAsString(any(TransferRequestedEvent.class))).thenReturn(expectedJson);

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);

        // Act
        transferService.transfer(validTransfer);

        // Assert
        verify(outboxEventRepository, times(1)).save(eventCaptor.capture());

        OutboxEvent capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);
        assertEquals("Transfer", capturedEvent.getAggregateType());
        assertEquals(validTransfer.getIdempotencyKey().toString(), capturedEvent.getAggregateId());
        assertEquals("TransferRequested", capturedEvent.getEventType());
        assertEquals(expectedJson, capturedEvent.getPayload());
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
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw JsonSerializationException when JSON serialization fails")
    void transfer_shouldThrowJsonSerializationException_whenSerializationFails() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(any(TransferRequestedEvent.class)))
                .thenThrow(new JsonProcessingException("Serialization error") {});

        // Act & Assert
        JsonSerializationException exception = assertThrows(JsonSerializationException.class, () -> {
            transferService.transfer(validTransfer);
        });

        assertTrue(exception.getMessage().contains("Error serializing transfer event"));
        verify(outboxEventRepository, never()).save(any());
    }
}
