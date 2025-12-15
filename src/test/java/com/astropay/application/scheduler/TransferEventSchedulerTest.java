package com.astropay.application.scheduler;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferEventSchedulerTest {

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
    private TransferEventScheduler scheduler;

    private Account senderAccount;
    private Account receiverAccount;
    private OutboxEvent outboxEvent;
    private UUID idempotencyKey;

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

        idempotencyKey = UUID.randomUUID();
        Map<String, Object> payloadMap = Map.of(
            "senderAccountId", 1L,
            "receiverAccountId", 2L,
            "amount", "100.00",
            "idempotencyKey", idempotencyKey.toString()
        );
        String payload = objectMapper.writeValueAsString(payloadMap);
        outboxEvent = spy(new OutboxEvent("Transfer", idempotencyKey.toString(), "TransferRequested", payload));
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(outboxEventRepository, transactionRepository, accountRepository, transactionAuditService);
    }

    @Test
    @DisplayName("Should do nothing when no events are found")
    void shouldDoNothingWhenNoEventsFound() {
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

        scheduler.processTransferEvents();

        verify(accountRepository, never()).findByIds(any());
        verify(outboxEventRepository, never()).deleteAllInBatch(any());
    }

    @Test
    @DisplayName("Should handle insufficient balance and fail the transaction")
    void shouldHandleInsufficientBalance() {
        // Arrange
        senderAccount.adjustBalance(new BigDecimal("50.00"));
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        when(accountRepository.findByIds(anyList())).thenReturn(List.of(senderAccount, receiverAccount));

        final AtomicReference<TransactionStatus> statusOnFirstSave = new AtomicReference<>();

        // Configure mock to capture status on FIRST call only, then return null on subsequent calls
        doAnswer(invocation -> {
            List<Transaction> transactions = invocation.getArgument(0);
            statusOnFirstSave.set(transactions.get(0).getStatus());
            return null;
        })
        .doAnswer(invocation -> null) // Correctly handle the second call for non-void method
        .when(transactionRepository).saveAll(any());

        // Act
        scheduler.processTransferEvents();

        // Assert
        // Verify the status captured during the first call
        assertEquals(TransactionStatus.PENDING, statusOnFirstSave.get());

        // Now, verify the second call and its final state
        ArgumentCaptor<List<Transaction>> finalTransactionCaptor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository, times(2)).saveAll(finalTransactionCaptor.capture());
        
        List<Transaction> finalTransactions = finalTransactionCaptor.getAllValues().get(1);
        assertEquals(TransactionStatus.FAILED, finalTransactions.get(0).getStatus());
        assertTrue(finalTransactions.get(0).getFailureReason().contains("Insufficient balance"));

        // Verify other interactions
        verify(transactionAuditService).createAuditEvent(any(Transaction.class), eq("TransactionFailed"));
        verify(outboxEventRepository).deleteAllInBatch(anyList());
    }

    @Test
    @DisplayName("Should mark event for retry on unexpected failure during processing")
    void shouldRetryOnUnexpectedFailure() {
        // Arrange
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        when(accountRepository.findByIds(anyList())).thenReturn(List.of(senderAccount, receiverAccount));
        
        doThrow(new RuntimeException("Audit service unavailable")).when(transactionAuditService).createAuditEvent(any(), any());

        // Act
        scheduler.processTransferEvents();

        // Assert
        verify(outboxEvent).incrementRetryCount();
        verify(outboxEvent).setStatus(OutboxEventStatus.UNPROCESSED);
        verify(outboxEventRepository, never()).deleteAllInBatch(any());
    }

    @Test
    @DisplayName("Should mark as FAILED after max retries")
    void shouldMarkAsFailedAfterMaxRetries() {
        // Arrange
        outboxEvent.setRetryCount(4);
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        when(accountRepository.findByIds(anyList())).thenReturn(List.of(senderAccount, receiverAccount));
        
        doThrow(new RuntimeException("Audit service unavailable")).when(transactionAuditService).createAuditEvent(any(), any());

        // Act
        scheduler.processTransferEvents();

        // Assert
        verify(outboxEvent).incrementRetryCount();
        verify(outboxEvent).setStatus(OutboxEventStatus.FAILED);
        
        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository, times(2)).saveAll(captor.capture());

        List<OutboxEvent> failedEvents = captor.getAllValues().get(1);
        assertTrue(failedEvents.stream().anyMatch(e -> e.getStatus() == OutboxEventStatus.FAILED));
    }
}
