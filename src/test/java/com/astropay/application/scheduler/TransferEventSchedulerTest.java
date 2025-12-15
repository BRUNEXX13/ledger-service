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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @InjectMocks
    private TransferEventScheduler scheduler;

    private Account senderAccount;
    private Account receiverAccount;
    private Transaction transaction;
    private OutboxEvent outboxEvent;

    @BeforeEach
    void setUp() {
        User senderUser = new User("Sender", "111", "sender@test.com", Role.ROLE_EMPLOYEE);
        User receiverUser = new User("Receiver", "222", "receiver@test.com", Role.ROLE_EMPLOYEE);

        senderAccount = spy(new Account(senderUser, new BigDecimal("200.00")));
        receiverAccount = spy(new Account(receiverUser, new BigDecimal("50.00")));

        transaction = spy(new Transaction(senderAccount, receiverAccount, new BigDecimal("100.00"), UUID.randomUUID()));
        // Set ID manually using ReflectionTestUtils since it's a generated value
        ReflectionTestUtils.setField(transaction, "id", 1L);

        String payload = String.format("{\"transactionId\": %d}", transaction.getId());
        outboxEvent = spy(new OutboxEvent("TransferRequested", String.valueOf(transaction.getId()), "TransferRequested", payload));
    }

    @Test
    @DisplayName("Scenario 1: Should do nothing when no events are found")
    void shouldDoNothingWhenNoEventsFound() {
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

        scheduler.processTransferEvents();

        verify(transactionRepository, never()).findById(anyLong());
        verify(outboxEventRepository, never()).deleteAllInBatch(any());
    }

    @Test
    @DisplayName("Scenario 2: Should process event successfully")
    void shouldProcessEventSuccessfully() {
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        scheduler.processTransferEvents();

        verify(senderAccount).withdraw(new BigDecimal("100.00"));
        verify(receiverAccount).deposit(new BigDecimal("100.00"));
        verify(accountRepository).save(senderAccount);
        verify(accountRepository).save(receiverAccount);
        verify(transaction).complete();
        verify(transactionRepository).save(transaction);
        verify(transactionAuditService).createAuditEvent(transaction, "TransactionCompleted");
        
        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository).deleteAllInBatch(captor.capture());
        List<OutboxEvent> deletedEvents = captor.getValue();
        assertEquals(1, deletedEvents.size());
        assertEquals(outboxEvent.getPayload(), deletedEvents.get(0).getPayload());
        
        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
    }

    @Test
    @DisplayName("Scenario 3: Should handle insufficient balance failure")
    void shouldHandleInsufficientBalance() {
        senderAccount.adjustBalance(new BigDecimal("50.00")); // Set balance lower than transfer amount
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        scheduler.processTransferEvents();

        verify(senderAccount).withdraw(new BigDecimal("100.00")); // Attempt to withdraw is still made
        verify(transaction).fail(anyString());
        verify(transactionRepository).save(transaction);
        verify(transactionAuditService).createAuditEvent(transaction, "TransactionFailed");
        verify(accountRepository, never()).save(any(Account.class));
        
        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository).deleteAllInBatch(captor.capture());
        List<OutboxEvent> deletedEvents = captor.getValue();
        assertEquals(1, deletedEvents.size());
        assertEquals(outboxEvent.getPayload(), deletedEvents.get(0).getPayload());
        
        assertEquals(TransactionStatus.FAILED, transaction.getStatus());
    }

    @Test
    @DisplayName("Scenario 4: Should retry on unexpected failure")
    void shouldRetryOnUnexpectedFailure() {
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        when(transactionRepository.findById(transaction.getId())).thenThrow(new RuntimeException("Database connection failed"));

        scheduler.processTransferEvents();

        verify(outboxEvent).incrementRetryCount();
        verify(outboxEvent).setStatus(OutboxEventStatus.UNPROCESSED);
        verify(outboxEventRepository, never()).deleteAllInBatch(any());
    }

    @Test
    @DisplayName("Scenario 5: Should mark as FAILED after max retries")
    void shouldMarkAsFailedAfterMaxRetries() {
        outboxEvent.setRetryCount(4);
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        when(transactionRepository.findById(transaction.getId())).thenThrow(new RuntimeException("Database connection failed"));

        scheduler.processTransferEvents();

        verify(outboxEvent).incrementRetryCount();
        verify(outboxEvent).setStatus(OutboxEventStatus.FAILED);
        
        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
        // Expect 2 calls: one for locking (PROCESSING) and one for marking as FAILED
        verify(outboxEventRepository, times(2)).saveAll(captor.capture());
        
        List<List<OutboxEvent>> allCapturedArguments = captor.getAllValues();
        
        // We are interested in the second call, which saves the failed events
        List<OutboxEvent> failedEvents = allCapturedArguments.get(1);
        
        boolean foundFailedEvent = failedEvents.stream()
            .anyMatch(e -> e.getPayload().equals(outboxEvent.getPayload()) && e.getStatus() == OutboxEventStatus.FAILED);
        assertTrue(foundFailedEvent, "Should have saved the event with FAILED status in the second call");
    }

    @Test
    @DisplayName("Scenario 6: Should skip already processed transaction")
    void shouldSkipAlreadyProcessedTransaction() {
        transaction.complete(); // Mark as already processed
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.singletonList(outboxEvent));
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        scheduler.processTransferEvents();

        verify(senderAccount, never()).withdraw(any());
        verify(receiverAccount, never()).deposit(any());
        
        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository).deleteAllInBatch(captor.capture());
        List<OutboxEvent> deletedEvents = captor.getValue();
        assertEquals(1, deletedEvents.size());
        assertEquals(outboxEvent.getPayload(), deletedEvents.get(0).getPayload());
    }
}
