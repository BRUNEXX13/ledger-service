package com.bss.application.scheduler;

import com.bss.application.service.transaction.TransactionAuditService;
import com.bss.domain.account.Account;
import com.bss.domain.account.AccountRepository;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.bss.domain.outbox.OutboxEventStatus;
import com.bss.domain.transaction.Transaction;
import com.bss.domain.transaction.TransactionRepository;
import com.bss.domain.transaction.TransactionStatus;
import com.bss.domain.user.Role;
import com.bss.domain.user.User;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyList;
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
        outboxEvent = createOutboxEvent(idempotencyKey, 1L, 2L, "100.00");
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

        verify(accountRepository, never()).findByIdForUpdate(any());
        verify(outboxEventRepository, never()).deleteAllInBatch(any());
    }

    @Test
    @DisplayName("Should handle insufficient balance and fail the transaction")
    void shouldHandleInsufficientBalance() {
        // Arrange
        senderAccount.adjustBalance(new BigDecimal("50.00"));
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));

        // Mock saveAll to return the list passed to it (simulating successful save of PENDING transaction)
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        scheduler.processTransferEvents();

        // Assert
        ArgumentCaptor<List<Transaction>> transactionCaptor = ArgumentCaptor.forClass(List.class);
        // saveAll called twice: 1. Initial save (PENDING), 2. Final update (FAILED)
        verify(transactionRepository, times(2)).saveAll(transactionCaptor.capture());
        
        List<Transaction> finalTransactions = transactionCaptor.getAllValues().get(1);
        assertEquals(TransactionStatus.FAILED, finalTransactions.get(0).getStatus());
        assertTrue(finalTransactions.get(0).getFailureReason().contains("Insufficient balance"));

        verify(transactionAuditService).createAuditEvent(any(Transaction.class), eq("TransactionFailed"));
        verify(outboxEventRepository).deleteAllInBatch(anyList());
    }

    @Test
    @DisplayName("Should mark event for retry on unexpected failure during processing")
    void shouldRetryOnUnexpectedFailure() {
        // Arrange
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        
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
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        
        doThrow(new RuntimeException("Audit service unavailable")).when(transactionAuditService).createAuditEvent(any(), any());

        // Act
        scheduler.processTransferEvents();

        // Assert
        verify(outboxEvent).incrementRetryCount();
        verify(outboxEvent).setStatus(OutboxEventStatus.FAILED);
        
        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository, times(2)).saveAll(captor.capture()); // Initial lock + Final save

        List<OutboxEvent> failedEvents = captor.getAllValues().get(1);
        assertTrue(failedEvents.stream().anyMatch(e -> e.getStatus() == OutboxEventStatus.FAILED));
    }

    @Test
    @DisplayName("Should handle DataIntegrityViolationException as idempotent success (mark as FAILED/PROCESSED)")
    void shouldHandleDataIntegrityViolation() {
        // Arrange
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        
        doThrow(new DataIntegrityViolationException("Duplicate key")).when(transactionAuditService).createAuditEvent(any(), any());

        // Act
        scheduler.processTransferEvents();

        // Assert
        verify(outboxEvent).setStatus(OutboxEventStatus.FAILED);
        verify(outboxEvent).setRetryCount(5); // MAX_RETRIES
        
        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository, times(2)).saveAll(captor.capture());
        
        List<OutboxEvent> savedEvents = captor.getAllValues().get(1);
        assertTrue(savedEvents.contains(outboxEvent));
        assertEquals(OutboxEventStatus.FAILED, outboxEvent.getStatus());
    }

    @Test
    @DisplayName("Should process successful transfer")
    void shouldProcessSuccessfulTransfer() {
        // Arrange
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        scheduler.processTransferEvents();

        // Assert
        assertEquals(new BigDecimal("100.00"), senderAccount.getBalance()); // 200 - 100
        assertEquals(new BigDecimal("150.00"), receiverAccount.getBalance()); // 50 + 100
        
        verify(transactionRepository, times(2)).saveAll(any()); // 1: PENDING, 2: SUCCESS (Update)
        verify(transactionAuditService).createAuditEvent(any(Transaction.class), eq("TransactionCompleted"));
        verify(outboxEventRepository).deleteAllInBatch(anyList());
    }

    @Test
    @DisplayName("Should handle batch save failure and retry individually")
    void shouldHandleBatchSaveFailureAndRetryIndividually() throws JsonProcessingException {
        // Arrange
        UUID key1 = UUID.randomUUID();
        UUID key2 = UUID.randomUUID();
        OutboxEvent event1 = createOutboxEvent(key1, 1L, 2L, "10.00");
        OutboxEvent event2 = createOutboxEvent(key2, 1L, 2L, "20.00");

        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Arrays.asList(event1, event2));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));

        // Configure saveAll to throw on first call (batch failure) and succeed on second (update success)
        doThrow(new DataIntegrityViolationException("Batch failed"))
            .doAnswer(inv -> inv.getArgument(0))
            .when(transactionRepository).saveAll(anyList());

        // Configure individual save:
        // We use any() because the order might vary depending on implementation details, 
        // but we want to ensure one succeeds and one fails.
        // To be precise, we can match by argument, but since Transaction objects are created inside the method, we can't match by reference.
        // We can match by amount or idempotency key.
        
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            if (tx.getIdempotencyKey().equals(key1)) {
                return tx; // Success for event1 (10.00)
            } else {
                throw new DataIntegrityViolationException("Individual duplicate"); // Fail for event2 (20.00)
            }
        });

        // Act
        scheduler.processTransferEvents();

        // Assert
        verify(transactionRepository, times(2)).saveAll(anyList()); // 1. Batch fail, 2. Update success
        verify(transactionRepository, times(2)).save(any(Transaction.class)); // 2 individual attempts

        // Verify balances: 200 - 10 = 190. (event2 failed, so 20 wasn't deducted)
        assertEquals(new BigDecimal("190.00"), senderAccount.getBalance());
        assertEquals(new BigDecimal("60.00"), receiverAccount.getBalance());  // 50 + 10

        // Verify event for failed transaction is marked as FAILED
        ArgumentCaptor<List<OutboxEvent>> failedEventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository, times(2)).saveAll(failedEventsCaptor.capture());
        // The second call to saveAll should contain the failed event
        List<OutboxEvent> savedFailedEvents = failedEventsCaptor.getAllValues().get(1);
        assertEquals(1, savedFailedEvents.size());
        assertEquals(OutboxEventStatus.FAILED, savedFailedEvents.get(0).getStatus());
        // Ensure it's event2
        assertTrue(savedFailedEvents.get(0).getPayload().contains(key2.toString()));

        // Verify event for successful transaction is deleted
        ArgumentCaptor<List<OutboxEvent>> processedEventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository).deleteAllInBatch(processedEventsCaptor.capture());
        assertEquals(1, processedEventsCaptor.getValue().size());
        // Ensure it's event1
        assertTrue(processedEventsCaptor.getValue().get(0).getPayload().contains(key1.toString()));
    }

    @Test
    @DisplayName("Should process a batch with mixed success and insufficient balance")
    void shouldProcessBatchWithMixedSuccessAndFailure() throws JsonProcessingException {
        // Arrange
        senderAccount.adjustBalance(new BigDecimal("100.00")); // Enough for one, not for both
        UUID key1 = UUID.randomUUID();
        UUID key2 = UUID.randomUUID();
        OutboxEvent event1 = createOutboxEvent(key1, 1L, 2L, "50.00");  // This should succeed
        OutboxEvent event2 = createOutboxEvent(key2, 1L, 2L, "60.00"); // This should fail

        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Arrays.asList(event1, event2));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        scheduler.processTransferEvents();

        // Assert
        // Balances should reflect only the successful transaction
        assertEquals(new BigDecimal("50.00"), senderAccount.getBalance()); // 100 - 50
        assertEquals(new BigDecimal("100.00"), receiverAccount.getBalance()); // 50 + 50

        // Verify transaction states
        ArgumentCaptor<List<Transaction>> transactionCaptor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository, times(2)).saveAll(transactionCaptor.capture());
        
        List<Transaction> finalTransactions = transactionCaptor.getAllValues().get(1);
        Transaction successTx = finalTransactions.stream().filter(t -> t.getAmount().compareTo(new BigDecimal("50.00")) == 0).findFirst().get();
        Transaction failedTx = finalTransactions.stream().filter(t -> t.getAmount().compareTo(new BigDecimal("60.00")) == 0).findFirst().get();
        
        assertEquals(TransactionStatus.SUCCESS, successTx.getStatus());
        assertEquals(TransactionStatus.FAILED, failedTx.getStatus());
        assertTrue(failedTx.getFailureReason().contains("Insufficient balance"));

        // Both events are "processed" (either completed or failed), so both should be deleted
        verify(outboxEventRepository).deleteAllInBatch(anyList());
        ArgumentCaptor<List<OutboxEvent>> deletedEventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository).deleteAllInBatch(deletedEventsCaptor.capture());
        assertEquals(2, deletedEventsCaptor.getValue().size());
    }

    @Test
    @DisplayName("Should mark event as FAILED if account is not found")
    void shouldMarkEventAsFailedIfAccountNotFound() {
        // Arrange
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty()); // Receiver not found

        // Act
        scheduler.processTransferEvents();

        // Assert
        verify(transactionRepository, never()).saveAll(any());
        verify(outboxEventRepository, never()).deleteAllInBatch(any());

        ArgumentCaptor<List<OutboxEvent>> failedEventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository, times(2)).saveAll(failedEventsCaptor.capture());
        List<OutboxEvent> savedFailedEvents = failedEventsCaptor.getAllValues().get(1);
        assertEquals(1, savedFailedEvents.size());
        assertEquals(OutboxEventStatus.FAILED, savedFailedEvents.get(0).getStatus());
    }

    @Test
    @DisplayName("Should mark event as FAILED for malformed payload")
    void shouldMarkEventAsFailedForMalformedPayload() {
        // Arrange
        OutboxEvent malformedEvent = new OutboxEvent("Transfer", "key", "TransferRequested", "{not-a-json}");
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(malformedEvent));

        // Act
        scheduler.processTransferEvents();

        // Assert
        verify(accountRepository, never()).findByIdForUpdate(any());
        verify(transactionRepository, never()).saveAll(any());
        
        ArgumentCaptor<List<OutboxEvent>> eventCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository, times(1)).saveAll(eventCaptor.capture()); // Only the initial status update
        assertEquals(OutboxEventStatus.FAILED, eventCaptor.getValue().get(0).getStatus());
    }

    @Test
    @DisplayName("Should mark event as FAILED if transaction is lost during save (Defensive)")
    void shouldMarkEventAsFailedIfTransactionLostDuringSave() {
        // Arrange
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        
        // Simulate a scenario where saveAll returns an empty list (transaction lost)
        when(transactionRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        // Act
        scheduler.processTransferEvents();

        // Assert
        verify(outboxEvent).setStatus(OutboxEventStatus.FAILED);
        verify(outboxEvent).setRetryCount(5); // MAX_RETRIES
        
        ArgumentCaptor<List<OutboxEvent>> failedEventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository, times(2)).saveAll(failedEventsCaptor.capture());
        List<OutboxEvent> savedFailedEvents = failedEventsCaptor.getAllValues().get(1);
        assertEquals(1, savedFailedEvents.size());
        assertEquals(OutboxEventStatus.FAILED, savedFailedEvents.get(0).getStatus());
    }

    // Helper to create events
    private OutboxEvent createOutboxEvent(UUID idempotencyKey, Long senderId, Long receiverId, String amount) throws JsonProcessingException {
        Map<String, Object> payloadMap = Map.of(
            "senderAccountId", senderId,
            "receiverAccountId", receiverId,
            "amount", amount,
            "idempotencyKey", idempotencyKey.toString()
        );
        String payload = objectMapper.writeValueAsString(payloadMap);
        return spy(new OutboxEvent("Transfer", idempotencyKey.toString(), "TransferRequested", payload));
    }
}
