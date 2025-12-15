package com.astropay.application.scheduler;

import com.astropay.application.service.transaction.TransactionAuditService;
import com.astropay.domain.model.account.Account;
import com.astropay.domain.model.account.AccountRepository;
import com.astropay.domain.model.account.InsufficientBalanceException;
import com.astropay.domain.model.outbox.OutboxEvent;
import com.astropay.domain.model.outbox.OutboxEventRepository;
import com.astropay.domain.model.outbox.OutboxEventStatus;
import com.astropay.domain.model.transaction.Transaction;
import com.astropay.domain.model.transaction.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class TransferEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(TransferEventScheduler.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionAuditService transactionAuditService;
    private final ObjectMapper objectMapper;

    public TransferEventScheduler(OutboxEventRepository outboxEventRepository,
                                  TransactionRepository transactionRepository,
                                  AccountRepository accountRepository,
                                  TransactionAuditService transactionAuditService,
                                  ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.transactionAuditService = transactionAuditService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 2000) // Aumentado para 2 segundos para dar tempo de processar
    @Transactional
    public void processTransferEvents() {
        LocalDateTime lockTimeout = LocalDateTime.now().minusMinutes(1);
        List<OutboxEvent> events = outboxEventRepository.findAndLockUnprocessedEvents(
                OutboxEventStatus.UNPROCESSED, "TransferRequested", lockTimeout, PageRequest.of(0, BATCH_SIZE));

        if (events.isEmpty()) {
            return;
        }

        log.info("Found {} events to process.", events.size());
        LocalDateTime newLockTime = LocalDateTime.now();
        for (OutboxEvent event : events) {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setLockedAt(newLockTime);
        }
        outboxEventRepository.saveAll(events);

        List<OutboxEvent> processedEvents = new ArrayList<>();
        List<OutboxEvent> failedEvents = new ArrayList<>();

        for (OutboxEvent event : events) {
            try {
                processTransfer(event);
                processedEvents.add(event);
            } catch (Exception e) {
                log.error("Failed to process outbox event {}. It will be retried or marked as FAILED.", event.getId(), e);
                event.incrementRetryCount();
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.setStatus(OutboxEventStatus.FAILED);
                    failedEvents.add(event);
                } else {
                    event.setStatus(OutboxEventStatus.UNPROCESSED); // Volta para a fila
                }
            }
        }

        if (!processedEvents.isEmpty()) {
            outboxEventRepository.deleteAllInBatch(processedEvents);
            log.info("Successfully processed and deleted {} events.", processedEvents.size());
        }
        if (!failedEvents.isEmpty()) {
            outboxEventRepository.saveAll(failedEvents);
            log.warn("Marked {} events as FAILED after multiple retries.", failedEvents.size());
        }
    }

    @Retryable(
            value = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 150)
    )
    public void processTransfer(OutboxEvent event) throws Exception {
        JsonNode payload = objectMapper.readTree(event.getPayload());
        Long transactionId = payload.get("transactionId").asLong();

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalStateException("Transaction not found for id: " + transactionId));

        if (!transaction.getStatus().equals(com.astropay.domain.model.transaction.TransactionStatus.PENDING)) {
            log.warn("Transaction {} already processed with status {}. Skipping.", transaction.getId(), transaction.getStatus());
            return;
        }

        Account senderAccount = transaction.getSender();
        Account receiverAccount = transaction.getReceiver();

        try {
            senderAccount.withdraw(transaction.getAmount());
            receiverAccount.deposit(transaction.getAmount());

            accountRepository.save(senderAccount);
            accountRepository.save(receiverAccount);

            transaction.complete();
            transactionAuditService.createAuditEvent(transaction, "TransactionCompleted");

        } catch (InsufficientBalanceException | IllegalStateException e) {
            log.warn("Transaction {} FAILED. Reason: {}", transaction.getId(), e.getMessage());
            transaction.fail(e.getMessage());
            transactionAuditService.createAuditEvent(transaction, "TransactionFailed");
        } finally {
            transactionRepository.save(transaction);
        }
    }
}
