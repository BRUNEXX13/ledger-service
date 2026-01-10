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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ExecutorService executorService;

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
    @DisplayName("Should submit tasks to executor service")
    void shouldSubmitTasksToExecutor() {
        scheduler.scheduleTransferProcessing();
        verify(executorService, times(8)).submit(any(Runnable.class));
    }

    @Test
    @DisplayName("Should process batch logic correctly when invoked")
    void shouldProcessBatchLogic() {
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        assertEquals(new BigDecimal("100.00"), senderAccount.getBalance());
        assertEquals(new BigDecimal("150.00"), receiverAccount.getBalance());
        
        verify(transactionRepository, times(2)).saveAll(any());
        verify(outboxEventRepository).deleteAllInBatch(anyList());
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

        verify(outboxEventRepository).deleteAllInBatch(anyList());
    }

    @Test
    @DisplayName("Should retry on unexpected failure")
    void shouldRetryOnUnexpectedFailure() {
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        
        doThrow(new RuntimeException("Error")).when(transactionAuditService).createAuditEvent(any(), any());

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        verify(outboxEvent).incrementRetryCount();
        verify(outboxEvent).setStatus(OutboxEventStatus.UNPROCESSED);
        verify(outboxEventRepository, never()).deleteAllInBatch(any());
    }

    @Test
    @DisplayName("Should handle DataIntegrityViolationException (Idempotency)")
    void shouldHandleDataIntegrityViolation() {
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        
        doThrow(new DataIntegrityViolationException("Duplicate")).when(transactionAuditService).createAuditEvent(any(), any());

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        verify(outboxEvent).setStatus(OutboxEventStatus.FAILED);
        verify(outboxEventRepository, times(2)).saveAll(anyList());
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
        verify(outboxEventRepository, times(1)).saveAll(anyList()); // Only status update
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should mark event as FAILED when payload is missing required fields")
    void shouldMarkEventAsFailedWhenPayloadIsMissingFields() throws JsonProcessingException {
        // Payload missing amount
        String payload = "{\"senderAccountId\": 1, \"receiverAccountId\": 2, \"idempotencyKey\": \"" + UUID.randomUUID() + "\"}";
        OutboxEvent invalidEvent = spy(new OutboxEvent("Transfer", "123", "TransferRequested", payload));
        
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(invalidEvent));

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        verify(invalidEvent).setStatus(OutboxEventStatus.FAILED);
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

        // Transaction should be created but failed
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository, times(2)).saveAll(captor.capture());
        
        Transaction failedTx = captor.getAllValues().get(1).get(0);
        assertEquals(TransactionStatus.FAILED, failedTx.getStatus());
        assertTrue(failedTx.getFailureReason().contains("Account is not active"));
        
        // Event should be processed (deleted) because the failure was handled logically
        verify(outboxEventRepository).deleteAllInBatch(anyList());
    }

    @Test
    @DisplayName("Should handle missing idempotency key in payload")
    void shouldHandleMissingIdempotencyKey() throws JsonProcessingException {
        // Payload missing idempotencyKey
        String payload = "{\"senderAccountId\": 1, \"receiverAccountId\": 2, \"amount\": \"100.00\"}";
        OutboxEvent invalidEvent = spy(new OutboxEvent("Transfer", "123", "TransferRequested", payload));
        
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(invalidEvent));

        ReflectionTestUtils.invokeMethod(scheduler, "processNextBatch");

        // Should be marked as FAILED because it throws NPE/Exception during preparation
        verify(invalidEvent).setStatus(OutboxEventStatus.FAILED);
        verify(transactionRepository, never()).saveAll(any());
    }

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
