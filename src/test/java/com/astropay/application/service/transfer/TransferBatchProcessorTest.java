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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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

    @Captor
    private ArgumentCaptor<List<Transaction>> transactionListCaptor;
    @Captor
    private ArgumentCaptor<List<OutboxEvent>> outboxEventListCaptor;

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

    @AfterEach
    void tearDown() {
        Mockito.reset(outboxEventRepository, transactionRepository, accountRepository, transactionAuditService);
    }

    @Test
    @DisplayName("Should process a valid batch of events successfully")
    void shouldProcessValidBatch() {
        when(accountRepository.findByIdsAndLock(anyList())).thenReturn(List.of(senderAccount, receiverAccount));
        when(transactionRepository.findAllByIdempotencyKeyIn(anyList())).thenReturn(Collections.emptyList());

        batchProcessor.processBatch(List.of(validEvent));

        verify(transactionRepository, times(2)).saveAll(transactionListCaptor.capture());
        
        assertEquals(TransactionStatus.SUCCESS, transactionListCaptor.getAllValues().get(1).getFirst().getStatus());

        assertEquals(new BigDecimal("100.00"), senderAccount.getBalance());
        assertEquals(new BigDecimal("150.00"), receiverAccount.getBalance());

        // CORREÇÃO: Usar any() pois values() retorna Collection, não List
        verify(accountRepository).saveAll(any());

        verify(outboxEventRepository).deleteAllInBatch(List.of(validEvent));
    }

    @Test
    @DisplayName("Should fallback to individual processing on batch failure")
    void shouldFallbackToIndividualProcessingOnBatchFailure() {
        when(accountRepository.findByIdsAndLock(anyList())).thenReturn(List.of(senderAccount, receiverAccount));
        when(transactionRepository.findAllByIdempotencyKeyIn(anyList())).thenReturn(Collections.emptyList());
        
        doThrow(new DataIntegrityViolationException("Duplicate key"))
            .doReturn(Collections.emptyList()) 
            .when(transactionRepository).saveAll(anyList());

        batchProcessor.processBatch(List.of(validEvent));

        verify(transactionRepository).save(any(Transaction.class));
        verify(transactionRepository, times(2)).saveAll(anyList());
        
        // CORREÇÃO: Usar any() pois values() retorna Collection, não List
        verify(accountRepository).saveAll(any());
        
        verify(outboxEventRepository).deleteAllInBatch(List.of(validEvent));
    }

    @Test
    @DisplayName("Should skip duplicate transaction if it already exists (caught by pre-filter)")
    void shouldSkipDuplicateTransaction() {
        when(accountRepository.findByIdsAndLock(anyList())).thenReturn(List.of(senderAccount, receiverAccount));
        
        UUID knownId = UUID.randomUUID();
        try {
            Map<String, Object> payloadMap = Map.of(
                "senderAccountId", 1L,
                "receiverAccountId", 2L,
                "amount", "100.00",
                "idempotencyKey", knownId.toString()
            );
            String payload = objectMapper.writeValueAsString(payloadMap);
            validEvent = new OutboxEvent("Transfer", knownId.toString(), "TransferRequested", payload);
            ReflectionTestUtils.setField(validEvent, "id", UUID.randomUUID());
        } catch (Exception e) { throw new RuntimeException(e); }

        Transaction duplicateTx = new Transaction(senderAccount, receiverAccount, BigDecimal.TEN, knownId);
        when(transactionRepository.findAllByIdempotencyKeyIn(anyList())).thenReturn(List.of(duplicateTx));

        batchProcessor.processBatch(List.of(validEvent));

        verify(transactionRepository, never()).saveAll(anyList());
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventRepository).deleteAllInBatch(List.of(validEvent));
    }

    @Test
    @DisplayName("Should handle insufficient balance and fail the transaction")
    void shouldHandleInsufficientBalance() {
        senderAccount.adjustBalance(new BigDecimal("50.00"));
        when(accountRepository.findByIdsAndLock(anyList())).thenReturn(List.of(senderAccount, receiverAccount));
        when(transactionRepository.findAllByIdempotencyKeyIn(anyList())).thenReturn(Collections.emptyList());

        batchProcessor.processBatch(List.of(validEvent));

        verify(transactionRepository, times(2)).saveAll(transactionListCaptor.capture());
        
        Transaction finalTransaction = transactionListCaptor.getAllValues().get(1).getFirst();
        assertEquals(TransactionStatus.FAILED, finalTransaction.getStatus());
        assertTrue(finalTransaction.getFailureReason().contains("Insufficient balance"));

        assertEquals(new BigDecimal("50.00"), senderAccount.getBalance());
        assertEquals(new BigDecimal("50.00"), receiverAccount.getBalance());

        // CORREÇÃO: Usar any() pois values() retorna Collection, não List
        verify(accountRepository).saveAll(any());

        verify(outboxEventRepository).deleteAllInBatch(List.of(validEvent));
    }

    @Test
    @DisplayName("Should mark event as FAILED if an account is not found")
    void shouldFailEventIfAccountNotFound() {
        when(accountRepository.findByIdsAndLock(anyList())).thenReturn(List.of(senderAccount));

        batchProcessor.processBatch(List.of(validEvent));

        verify(outboxEventRepository).saveAll(outboxEventListCaptor.capture());
        OutboxEvent failedEvent = outboxEventListCaptor.getValue().getFirst();
        assertEquals(OutboxEventStatus.FAILED, failedEvent.getStatus());
        
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should retry event on unexpected error")
    void shouldRetryEventOnUnexpectedError() {
        when(accountRepository.findByIdsAndLock(anyList())).thenReturn(List.of(senderAccount, receiverAccount));
        when(transactionRepository.findAllByIdempotencyKeyIn(anyList())).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("Audit service failed")).when(transactionAuditService).createAuditEvent(any(), anyString());

        batchProcessor.processBatch(List.of(validEvent));

        assertEquals(1, validEvent.getRetryCount());
        assertEquals(OutboxEventStatus.UNPROCESSED, validEvent.getStatus());

        verify(outboxEventRepository).saveAll(outboxEventListCaptor.capture());
        assertEquals(validEvent, outboxEventListCaptor.getValue().getFirst());

        verify(outboxEventRepository, never()).deleteAllInBatch(any());
    }

    @Test
    @DisplayName("Should mark event as FAILED after max retries")
    void shouldFailEventAfterMaxRetries() {
        validEvent.setRetryCount(4);
        when(accountRepository.findByIdsAndLock(anyList())).thenReturn(List.of(senderAccount, receiverAccount));
        when(transactionRepository.findAllByIdempotencyKeyIn(anyList())).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("Audit service failed")).when(transactionAuditService).createAuditEvent(any(), anyString());

        batchProcessor.processBatch(List.of(validEvent));

        assertEquals(5, validEvent.getRetryCount());
        assertEquals(OutboxEventStatus.FAILED, validEvent.getStatus());
        
        verify(outboxEventRepository).saveAll(outboxEventListCaptor.capture());
        assertTrue(outboxEventListCaptor.getValue().stream().anyMatch(e -> e.getStatus() == OutboxEventStatus.FAILED));
    }
}
