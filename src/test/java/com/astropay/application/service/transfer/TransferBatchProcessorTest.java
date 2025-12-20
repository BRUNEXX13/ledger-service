package com.astropay.application.service.transfer;

import com.astropay.application.service.transaction.TransactionAuditService;
import com.astropay.domain.model.account.Account;
import com.astropay.domain.model.account.AccountRepository;
import com.astropay.domain.model.outbox.OutboxEvent;
import com.astropay.domain.model.outbox.OutboxEventRepository;
import com.astropay.domain.model.outbox.OutboxEventStatus;
import com.astropay.domain.model.transaction.Transaction;
import com.astropay.domain.model.transaction.TransactionRepository;
import com.astropay.domain.model.transaction.TransactionStatus;
import com.astropay.domain.model.user.Role;
import com.astropay.domain.model.user.User;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferBatchProcessorTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionAuditService transactionAuditService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TransferBatchProcessor batchProcessor;

    private Account senderAccount;
    private Account receiverAccount;
    private OutboxEvent validEvent;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        User senderUser = new User("Sender", "111", "sender@test.com", Role.ROLE_EMPLOYEE);
        ReflectionTestUtils.setField(senderUser, "id", 1L);
        User receiverUser = new User("Receiver", "222", "receiver@test.com", Role.ROLE_EMPLOYEE);
        ReflectionTestUtils.setField(receiverUser, "id", 2L);

        senderAccount = new Account(senderUser, new BigDecimal("200.00"));
        ReflectionTestUtils.setField(senderAccount, "id", 1L);
        receiverAccount = new Account(receiverUser, new BigDecimal("50.00"));
        ReflectionTestUtils.setField(receiverAccount, "id", 2L);

        Map<String, Object> payloadMap = Map.of(
            "senderAccountId", 1L,
            "receiverAccountId", 2L,
            "amount", "100.00",
            "idempotencyKey", UUID.randomUUID().toString()
        );
        String payload = objectMapper.writeValueAsString(payloadMap);
        validEvent = new OutboxEvent("Transfer", UUID.randomUUID().toString(), "TransferRequested", payload);
        ReflectionTestUtils.setField(validEvent, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("Should process a valid batch of events successfully")
    void shouldProcessValidBatch() {
        // Arrange
        when(accountRepository.findByIds(anyList())).thenReturn(List.of(senderAccount, receiverAccount));

        // Act
        batchProcessor.processBatch(List.of(validEvent));

        // Assert
        ArgumentCaptor<List<Transaction>> transactionCaptor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository, times(2)).saveAll(transactionCaptor.capture());

        // Check final state of the transaction
        assertEquals(TransactionStatus.SUCCESS, transactionCaptor.getAllValues().get(1).get(0).getStatus());

        assertEquals(new BigDecimal("100.00"), senderAccount.getBalance());
        assertEquals(new BigDecimal("150.00"), receiverAccount.getBalance());

        ArgumentCaptor<List<Account>> accountCaptor = ArgumentCaptor.forClass(List.class);
        verify(accountRepository).saveAll(accountCaptor.capture());
        assertEquals(2, accountCaptor.getValue().size());

        verify(outboxEventRepository).deleteAllInBatch(List.of(validEvent));
    }

    @Test
    @DisplayName("Should handle insufficient balance and fail the transaction")
    void shouldHandleInsufficientBalance() {
        // Arrange
        senderAccount.adjustBalance(new BigDecimal("50.00")); // Not enough balance
        when(accountRepository.findByIds(anyList())).thenReturn(List.of(senderAccount, receiverAccount));

        // Act
        batchProcessor.processBatch(List.of(validEvent));

        // Assert
        ArgumentCaptor<List<Transaction>> transactionCaptor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository, times(2)).saveAll(transactionCaptor.capture());

        Transaction finalTransaction = transactionCaptor.getAllValues().get(1).get(0);
        assertEquals(TransactionStatus.FAILED, finalTransaction.getStatus());
        assertTrue(finalTransaction.getFailureReason().contains("Insufficient balance"));

        // Balances should not have changed
        assertEquals(new BigDecimal("50.00"), senderAccount.getBalance());
        assertEquals(new BigDecimal("50.00"), receiverAccount.getBalance());

        verify(outboxEventRepository).deleteAllInBatch(List.of(validEvent));
    }

    @Test
    @DisplayName("Should mark event as FAILED if an account is not found")
    void shouldFailEventIfAccountNotFound() {
        // Arrange
        when(accountRepository.findByIds(anyList())).thenReturn(List.of(senderAccount)); // Receiver is missing

        // Act
        batchProcessor.processBatch(List.of(validEvent));

        // Assert
        ArgumentCaptor<List<OutboxEvent>> failedEventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository).saveAll(failedEventsCaptor.capture());
        
        OutboxEvent failedEvent = failedEventsCaptor.getValue().get(0);
        assertEquals(OutboxEventStatus.FAILED, failedEvent.getStatus());
        
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should retry event on unexpected error")
    void shouldRetryEventOnUnexpectedError() {
        // Arrange
        when(accountRepository.findByIds(anyList())).thenReturn(List.of(senderAccount, receiverAccount));
        // Use any() to be more generic and avoid stubbing issues
        doThrow(new RuntimeException("Database connection failed")).when(accountRepository).saveAll(any());

        // Act
        batchProcessor.processBatch(List.of(validEvent));

        // Assert
        assertEquals(1, validEvent.getRetryCount());
        assertEquals(OutboxEventStatus.UNPROCESSED, validEvent.getStatus());
        verify(outboxEventRepository, never()).deleteAllInBatch(any());
    }

    @Test
    @DisplayName("Should mark event as FAILED after max retries")
    void shouldFailEventAfterMaxRetries() {
        // Arrange
        validEvent.setRetryCount(4);
        when(accountRepository.findByIds(anyList())).thenReturn(List.of(senderAccount, receiverAccount));
        doThrow(new RuntimeException("Database connection failed")).when(accountRepository).saveAll(any());

        // Act
        batchProcessor.processBatch(List.of(validEvent));

        // Assert
        assertEquals(5, validEvent.getRetryCount());
        assertEquals(OutboxEventStatus.FAILED, validEvent.getStatus());
        
        ArgumentCaptor<List<OutboxEvent>> failedEventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository).saveAll(failedEventsCaptor.capture());
        assertTrue(failedEventsCaptor.getValue().stream().anyMatch(e -> e.getStatus() == OutboxEventStatus.FAILED));
    }
}
