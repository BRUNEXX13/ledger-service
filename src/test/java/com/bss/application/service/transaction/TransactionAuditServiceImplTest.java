package com.bss.application.service.transaction;

import com.bss.application.dto.response.transaction.TransactionUserResponse;
import com.bss.application.event.transactions.TransactionEvent;
import com.bss.application.exception.ResourceNotFoundException;
import com.bss.domain.account.Account;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.bss.domain.transaction.Transaction;
import com.bss.domain.transaction.TransactionRepository;
import com.bss.domain.user.User;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionAuditServiceImplTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TransactionAuditServiceImpl auditService;

    private Transaction transaction;
    private Account sender;
    private Account receiver;

    @BeforeEach
    void setUp() {
        sender = mock(Account.class);
        // Use lenient() because these stubs might not be used in findUserByTransactionId tests
        lenient().when(sender.getId()).thenReturn(1L);
        
        receiver = mock(Account.class);
        lenient().when(receiver.getId()).thenReturn(2L);

        transaction = new Transaction(sender, receiver, BigDecimal.TEN, UUID.randomUUID());
        ReflectionTestUtils.setField(transaction, "id", 100L);
    }

    @Test
    @DisplayName("Should create audit event successfully")
    void shouldCreateAuditEventSuccessfully() throws JsonProcessingException {
        // Arrange
        String eventType = "TransactionCompleted";
        String json = "{\"id\":100}";
        when(objectMapper.writeValueAsString(any(TransactionEvent.class))).thenReturn(json);

        // Act
        auditService.createAuditEvent(transaction, eventType);

        // Assert
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        
        OutboxEvent savedEvent = captor.getValue();
        assertEquals("Transaction", savedEvent.getAggregateType());
        assertEquals("100", savedEvent.getAggregateId());
        assertEquals(eventType, savedEvent.getEventType());
        assertEquals(json, savedEvent.getPayload());
    }

    @Test
    @DisplayName("Should handle serialization error gracefully (log and return)")
    void shouldHandleSerializationError() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(any(TransactionEvent.class)))
                .thenThrow(new JsonProcessingException("Serialization failed") {});

        // Act
        auditService.createAuditEvent(transaction, "TransactionCompleted");

        // Assert
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should find user by transaction ID")
    void shouldFindUserByTransactionId() {
        // Arrange
        User user = mock(User.class);
        when(user.getName()).thenReturn("John");
        when(user.getEmail()).thenReturn("john@test.com");
        when(user.getDocument()).thenReturn("123");
        
        when(sender.getUser()).thenReturn(user);
        
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(transaction));

        // Act
        TransactionUserResponse response = auditService.findUserByTransactionId(100L);

        // Assert
        assertNotNull(response);
        assertEquals(100L, response.getTransactionId());
        assertEquals("John", response.getSenderName());
    }

    @Test
    @DisplayName("Should throw exception when transaction not found")
    void shouldThrowExceptionWhenTransactionNotFound() {
        // Arrange
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> auditService.findUserByTransactionId(999L));
    }
}
