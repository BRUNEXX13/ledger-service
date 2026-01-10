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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferEventSchedulerTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionAuditService transactionAuditService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private ExecutorService executorService;
    @Mock private org.springframework.transaction.TransactionStatus transactionStatus;

    @Captor private ArgumentCaptor<Runnable> runnableCaptor;
    @Captor private ArgumentCaptor<TransactionCallback<Object>> transactionCallbackCaptor;

    private TransferEventScheduler scheduler;
    private Account senderAccount;
    private Account receiverAccount;
    private OutboxEvent outboxEvent;
    private UUID idempotencyKey;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        scheduler = new TransferEventScheduler(
                outboxEventRepository,
                transactionRepository,
                accountRepository,
                transactionAuditService,
                objectMapper,
                transactionManager
        );
        
        ReflectionTestUtils.setField(scheduler, "transactionTemplate", transactionTemplate);
        ReflectionTestUtils.setField(scheduler, "executorService", executorService);

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
        Mockito.reset(outboxEventRepository, transactionRepository, accountRepository, transactionAuditService, transactionTemplate, executorService);
    }

    @Test
    @DisplayName("Full Flow: Should schedule, execute in transaction, and process batch")
    void shouldExecuteFullProcessingFlow() {
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.scheduleTransferProcessing();

        verify(executorService, times(8)).submit(runnableCaptor.capture());
        Runnable task = runnableCaptor.getAllValues().get(0);
        task.run();

        verify(transactionTemplate).execute(transactionCallbackCaptor.capture());
        TransactionCallback<Object> callback = transactionCallbackCaptor.getValue();
        callback.doInTransaction(transactionStatus);

        assertEquals(new BigDecimal("100.00"), senderAccount.getBalance());
        assertEquals(new BigDecimal("150.00"), receiverAccount.getBalance());
        verify(transactionRepository, times(2)).saveAll(any());
        verify(outboxEventRepository).deleteAllInBatch(anyList());
    }

    @Test
    @DisplayName("Should handle batch save failure and retry individually (Fallback Logic)")
    void shouldHandleBatchSaveFailureAndRetryIndividually() throws JsonProcessingException {
        UUID key1 = UUID.randomUUID();
        UUID key2 = UUID.randomUUID();
        OutboxEvent event1 = createOutboxEvent(key1, 1L, 2L, "10.00");
        OutboxEvent event2 = createOutboxEvent(key2, 1L, 2L, "20.00");

        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Arrays.asList(event1, event2));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));

        doThrow(new DataIntegrityViolationException("Batch failed"))
            .doAnswer(inv -> inv.getArgument(0))
            .when(transactionRepository).saveAll(anyList());

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            if (tx.getIdempotencyKey().equals(key1)) return tx;
            throw new DataIntegrityViolationException("Individual duplicate");
        });

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        verify(transactionRepository, times(2)).saveAll(anyList());
        verify(transactionRepository, times(2)).save(any(Transaction.class));

        ArgumentCaptor<List<OutboxEvent>> failedCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository, times(2)).saveAll(failedCaptor.capture());
        
        List<OutboxEvent> failedEvents = failedCaptor.getAllValues().get(1);
        assertTrue(failedEvents.stream().anyMatch(e -> e.getPayload().contains(key2.toString()) && e.getStatus() == OutboxEventStatus.FAILED));
    }

    @Test
    @DisplayName("Should handle missing transaction in execution phase")
    void shouldHandleMissingTransactionInExecutionPhase() {
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        
        when(transactionRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        verify(outboxEvent).setStatus(OutboxEventStatus.FAILED);
        verify(outboxEvent).setRetryCount(5);
        
        ArgumentCaptor<List<OutboxEvent>> failedCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository, times(2)).saveAll(failedCaptor.capture());
        
        List<OutboxEvent> failedEvents = failedCaptor.getAllValues().get(1);
        assertEquals(1, failedEvents.size());
        assertEquals(outboxEvent, failedEvents.get(0));
    }

    @Test
    @DisplayName("Should handle duplicate events in batch (Idempotency Map Logic)")
    void shouldHandleDuplicateEventsInBatch() throws JsonProcessingException {
        // Arrange: Two events with SAME idempotency key
        OutboxEvent event1 = createOutboxEvent(idempotencyKey, 1L, 2L, "10.00");
        OutboxEvent event2 = createOutboxEvent(idempotencyKey, 1L, 2L, "10.00"); // Duplicate
        
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Arrays.asList(event1, event2));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        
        // Simulate Batch Failure due to duplicate key
        doThrow(new DataIntegrityViolationException("Duplicate key in batch"))
            .doAnswer(inv -> inv.getArgument(0)) // Second call (update) succeeds
            .when(transactionRepository).saveAll(anyList());

        // Simulate Individual Save: First succeeds, Second fails
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(inv -> inv.getArgument(0)) // First call success
            .thenThrow(new DataIntegrityViolationException("Duplicate key individual")); // Second call fail

        // Act
        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        // Assert
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        
        ArgumentCaptor<List<OutboxEvent>> failedCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository, times(2)).saveAll(failedCaptor.capture());
        
        List<OutboxEvent> failedEvents = failedCaptor.getAllValues().get(1);
        assertEquals(1, failedEvents.size());
        assertEquals(OutboxEventStatus.FAILED, failedEvents.get(0).getStatus());
        
        verify(outboxEventRepository).deleteAllInBatch(anyList());
    }

    @Test
    @DisplayName("Should shutdown executor service gracefully")
    void shouldShutdownGracefully() throws InterruptedException {
        when(executorService.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);
        scheduler.shutdown();
        verify(executorService).shutdown();
        verify(executorService).awaitTermination(5, TimeUnit.SECONDS);
        verify(executorService, never()).shutdownNow();
    }

    @Test
    @DisplayName("Should force shutdown if termination times out")
    void shouldForceShutdownOnTimeout() throws InterruptedException {
        when(executorService.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(false);
        scheduler.shutdown();
        verify(executorService).shutdown();
        verify(executorService).awaitTermination(5, TimeUnit.SECONDS);
        verify(executorService).shutdownNow();
    }

    @Test
    @DisplayName("Should handle interruption during shutdown")
    void shouldHandleInterruptionDuringShutdown() throws InterruptedException {
        when(executorService.awaitTermination(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
        scheduler.shutdown();
        verify(executorService).shutdownNow();
    }

    @Test
    @DisplayName("Should handle exception inside parallel thread execution")
    void shouldHandleExceptionInParallelThread() {
        doThrow(new RuntimeException("Thread error")).when(transactionTemplate).execute(any());
        scheduler.scheduleTransferProcessing();
        verify(executorService, times(8)).submit(runnableCaptor.capture());
        Runnable task = runnableCaptor.getAllValues().get(0);
        assertDoesNotThrow(task::run);
    }

    @Test
    @DisplayName("Should handle missing idempotency key (Bug Fix Verification)")
    void shouldHandleMissingIdempotencyKey() throws JsonProcessingException {
        String payload = "{\"senderAccountId\": 1, \"receiverAccountId\": 2, \"amount\": \"100.00\"}";
        OutboxEvent invalidEvent = spy(new OutboxEvent("Transfer", "123", "TransferRequested", payload));
        
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(invalidEvent));

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        verify(invalidEvent).setStatus(OutboxEventStatus.FAILED);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should handle insufficient balance correctly")
    void shouldHandleInsufficientBalance() {
        senderAccount.adjustBalance(new BigDecimal("50.00"));
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        ArgumentCaptor<List<Transaction>> transactionCaptor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository, times(2)).saveAll(transactionCaptor.capture());
        
        List<Transaction> finalTransactions = transactionCaptor.getAllValues().get(1);
        assertEquals(TransactionStatus.FAILED, finalTransactions.get(0).getStatus());
        assertTrue(finalTransactions.get(0).getFailureReason().contains("Insufficient balance"));
    }

    @Test
    @DisplayName("Should mark event as FAILED when payload is invalid JSON")
    void shouldMarkEventAsFailedWhenPayloadIsInvalid() {
        OutboxEvent invalidEvent = new OutboxEvent("Transfer", "123", "TransferRequested", "{invalid-json");
        OutboxEvent spyEvent = spy(invalidEvent);
        
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(spyEvent));

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        verify(spyEvent).setStatus(OutboxEventStatus.FAILED);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should mark event as FAILED when account is blocked")
    void shouldMarkEventAsFailedWhenAccountIsBlocked() {
        senderAccount.block();
        
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository, times(2)).saveAll(captor.capture());
        
        Transaction failedTx = captor.getAllValues().get(1).get(0);
        assertEquals(TransactionStatus.FAILED, failedTx.getStatus());
    }

    // --- Tests moved from Coverage Test ---

    @Test
    @DisplayName("Should handle generic processing error (Runtime Exception)")
    void shouldHandleGenericProcessingError() {
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        
        Account sender = mock(Account.class);
        when(sender.getId()).thenReturn(1L);
        Account receiver = mock(Account.class);
        when(receiver.getId()).thenReturn(2L);
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiver));
        
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        
        doThrow(new RuntimeException("Generic Error")).when(sender).withdraw(any());

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        assertEquals(1, outboxEvent.getRetryCount());
        assertEquals(OutboxEventStatus.UNPROCESSED, outboxEvent.getStatus());
    }

    @Test
    @DisplayName("Should handle DataIntegrityViolationException as cause (Nested Exception)")
    void shouldHandleDataIntegrityViolationAsCause() {
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        
        Account sender = mock(Account.class);
        when(sender.getId()).thenReturn(1L);
        Account receiver = mock(Account.class);
        when(receiver.getId()).thenReturn(2L);
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiver));
        
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        
        RuntimeException nestedException = new RuntimeException("Wrapper", new DataIntegrityViolationException("Duplicate"));
        doThrow(nestedException).when(sender).withdraw(any());

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        assertEquals(OutboxEventStatus.FAILED, outboxEvent.getStatus());
    }

    @Test
    @DisplayName("Should skip event if status is FAILED")
    void shouldSkipEventIfStatusIsFailed() {
        outboxEvent.setStatus(OutboxEventStatus.FAILED);
        
        Account sender = mock(Account.class);
        when(sender.getId()).thenReturn(1L);
        Account receiver = mock(Account.class);
        when(receiver.getId()).thenReturn(2L);
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiver));

        // Invoke processBatchLogic directly to bypass status reset in processNextBatch
        ReflectionTestUtils.invokeMethod(scheduler, "processBatchLogic", Collections.singletonList(outboxEvent));

        verify(transactionRepository, never()).saveAll(any());
        verify(sender, never()).withdraw(any());
    }

    @Test
    @DisplayName("Should handle missing account in createTransactionFromEvent")
    void shouldHandleMissingAccountInCreateTransaction() throws JsonProcessingException {
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        assertEquals(OutboxEventStatus.FAILED, outboxEvent.getStatus());
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should handle JsonProcessingException in extractIdempotencyKey")
    void shouldHandleJsonProcessingExceptionInExtractIdempotencyKey() throws JsonProcessingException {
        OutboxEvent malformedEvent = new OutboxEvent("Transfer", "1", "TransferRequested", "{invalid-json");
        ReflectionTestUtils.setField(malformedEvent, "id", UUID.randomUUID());
        
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(malformedEvent));

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        assertEquals(OutboxEventStatus.FAILED, malformedEvent.getStatus());
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException in extractIdempotencyKey (Invalid UUID)")
    void shouldHandleIllegalArgumentExceptionInExtractIdempotencyKey() throws JsonProcessingException {
        String payload = "{\"senderAccountId\": 1, \"receiverAccountId\": 2, \"amount\": 100, \"idempotencyKey\": \"not-a-uuid\"}";
        OutboxEvent invalidUuidEvent = new OutboxEvent("Transfer", "1", "TransferRequested", payload);
        ReflectionTestUtils.setField(invalidUuidEvent, "id", UUID.randomUUID());
        
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(invalidUuidEvent));
        
        Account sender = mock(Account.class);
        when(sender.getId()).thenReturn(1L);
        Account receiver = mock(Account.class);
        when(receiver.getId()).thenReturn(2L);
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiver));

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        assertEquals(OutboxEventStatus.FAILED, invalidUuidEvent.getStatus());
    }
    
    @Test
    @DisplayName("Should cover idempotency key null check in processEvent")
    void shouldCoverIdempotencyKeyNullCheckInProcessEvent() throws JsonProcessingException {
        // Create mocks for this specific test to avoid NotAMockException
        Account mockSender = mock(Account.class);
        when(mockSender.getId()).thenReturn(1L);
        Account mockReceiver = mock(Account.class);
        when(mockReceiver.getId()).thenReturn(2L);
        
        JsonNode validNode = new ObjectMapper().readTree(outboxEvent.getPayload());
        JsonNode invalidNode = new ObjectMapper().readTree("{\"senderAccountId\": 1, \"receiverAccountId\": 2, \"amount\": 100}"); // No key
        
        // Stub readTree to return validNode first (for preparation), then invalidNode (for execution)
        doReturn(validNode).doReturn(invalidNode).when(objectMapper).readTree(anyString());
        
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockSender));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(mockReceiver));
        
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        // Assert
        verify(outboxEvent).setStatus(OutboxEventStatus.FAILED);
        // Verify on the MOCK, not the real object
        verify(mockSender, never()).withdraw(any());
    }
    
    @Test
    @DisplayName("Should log and rethrow critical error in batch logic")
    void shouldLogAndRethrowCriticalErrorInBatch() {
        // Arrange
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        
        // Force a critical error (e.g. DB timeout) during account fetch
        when(accountRepository.findByIdForUpdate(any())).thenThrow(new QueryTimeoutException("DB Timeout"));

        // Act & Assert
        assertThrows(QueryTimeoutException.class, () -> 
            ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch")
        );
    }

    private OutboxEvent createOutboxEvent(UUID idempotencyKey, Long senderId, Long receiverId, String amount) throws JsonProcessingException {
        Map<String, Object> payloadMap = Map.of(
            "senderAccountId", senderId,
            "receiverAccountId", receiverId,
            "amount", amount,
            "idempotencyKey", idempotencyKey.toString()
        );
        String payload = objectMapper.writeValueAsString(payloadMap);
        OutboxEvent event = new OutboxEvent("Transfer", idempotencyKey.toString(), "TransferRequested", payload);
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        return spy(event);
    }
}
