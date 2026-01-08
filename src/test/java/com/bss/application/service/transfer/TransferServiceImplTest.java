package com.bss.application.service.transfer;

import com.bss.application.event.transactions.TransferRequestedEvent;
import com.bss.domain.transfer.Transfer;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock
    private TransferBufferService transferBufferService;

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
    @DisplayName("Should enqueue TransferRequestedEvent for a valid transfer")
    void transfer_shouldEnqueueEvent_forValidTransfer() {
        // Arrange
        ArgumentCaptor<TransferRequestedEvent> eventCaptor = ArgumentCaptor.forClass(TransferRequestedEvent.class);

        // Act
        transferService.transfer(validTransfer);

        // Assert
        verify(transferBufferService, times(1)).enqueue(eventCaptor.capture());

        TransferRequestedEvent capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);
        assertEquals(validTransfer.getSenderAccountId(), capturedEvent.senderAccountId());
        assertEquals(validTransfer.getReceiverAccountId(), capturedEvent.receiverAccountId());
        assertEquals(validTransfer.getAmount(), capturedEvent.amount());
        assertEquals(validTransfer.getIdempotencyKey(), capturedEvent.idempotencyKey());
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
        verify(transferBufferService, never()).enqueue(any());
    }
}
