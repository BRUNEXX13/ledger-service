package com.bss.application.service.transaction;

import com.bss.application.dto.response.transaction.TransactionUserResponse;
import com.bss.application.event.transactions.TransactionEvent;
import com.bss.application.exception.ResourceNotFoundException;
import com.bss.domain.model.account.Account;
import com.bss.domain.model.outbox.OutboxEvent;
import com.bss.domain.model.outbox.OutboxEventRepository;
import com.bss.domain.model.transaction.Transaction;
import com.bss.domain.model.transaction.TransactionRepository;
import com.bss.domain.model.user.User;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // Permite stubs não utilizados no setUp
class TransactionAuditServiceImplTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TransactionAuditServiceImpl transactionAuditService;

    private Transaction transaction;
    private User user;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getName()).thenReturn("John Doe");
        when(user.getEmail()).thenReturn("john@example.com");

        Account sender = mock(Account.class);
        when(sender.getId()).thenReturn(10L);
        when(sender.getUser()).thenReturn(user);

        Account receiver = mock(Account.class);
        when(receiver.getId()).thenReturn(20L);

        transaction = mock(Transaction.class);
        when(transaction.getId()).thenReturn(100L);
        when(transaction.getSender()).thenReturn(sender);
        when(transaction.getReceiver()).thenReturn(receiver);
        when(transaction.getAmount()).thenReturn(BigDecimal.TEN);
        when(transaction.getCreatedAt()).thenReturn(Instant.now());
        when(transaction.getIdempotencyKey()).thenReturn(UUID.randomUUID());
    }

    @Test
    @DisplayName("Should create audit event successfully")
    void shouldCreateAuditEventSuccessfully() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(any(TransactionEvent.class))).thenReturn("{\"id\":100}");

        // Act
        transactionAuditService.createAuditEvent(transaction, "TransactionCompleted");

        // Assert
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());

        OutboxEvent savedEvent = eventCaptor.getValue();
        assertEquals("Transaction", savedEvent.getAggregateType());
        assertEquals("100", savedEvent.getAggregateId());
        assertEquals("TransactionCompleted", savedEvent.getEventType());
        assertEquals("{\"id\":100}", savedEvent.getPayload());
    }

    @Test
    @DisplayName("Should handle JSON serialization error gracefully")
    void shouldHandleJsonSerializationError() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Error") {});

        // Act
        transactionAuditService.createAuditEvent(transaction, "TransactionCompleted");

        // Assert
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should find user by transaction ID successfully")
    void shouldFindUserByTransactionId() {
        // Arrange
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(transaction));

        // Act
        TransactionUserResponse response = transactionAuditService.findUserByTransactionId(100L);

        // Assert
        assertNotNull(response);
        assertEquals("John Doe", response.getSenderName());
        assertEquals("john@example.com", response.getSenderEmail());
    }

    @Test
    @DisplayName("Should throw exception when transaction not found")
    void shouldThrowExceptionWhenTransactionNotFound() {
        // Arrange
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> transactionAuditService.findUserByTransactionId(999L));
    }
}
