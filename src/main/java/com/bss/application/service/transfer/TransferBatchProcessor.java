package com.bss.application.service.transfer;

import com.bss.application.service.transaction.TransactionAuditService;
import com.bss.domain.model.account.Account;
import com.bss.domain.model.account.AccountRepository;
import com.bss.domain.model.account.InsufficientBalanceException;
import com.bss.domain.model.outbox.OutboxEvent;
import com.bss.domain.model.outbox.OutboxEventRepository;
import com.bss.domain.model.outbox.OutboxEventStatus;
import com.bss.domain.model.transaction.Transaction;
import com.bss.domain.model.transaction.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TransferBatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransferBatchProcessor.class);
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionAuditService transactionAuditService;
    private final ObjectMapper objectMapper;

    public TransferBatchProcessor(OutboxEventRepository outboxEventRepository,
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

    @Transactional
    public void processBatch(List<OutboxEvent> events) {
        var batchContext = prepareBatchContext(events);
        
        // 1. Filter out transactions that already exist (Optimization & Idempotency)
        filterExistingTransactions(batchContext);

        if (!batchContext.transactions.isEmpty()) {
            try {
                // 2. Try batch save
                transactionRepository.saveAll(batchContext.transactions);
            } catch (DataIntegrityViolationException e) {
                // 3. Fallback: If race condition occurred after filter, save individually
                log.warn("Batch save failed despite pre-filtering. Falling back to single processing.", e);
                saveTransactionsIndividually(batchContext);
            }
        }

        executeTransactions(batchContext);
        persistResults(batchContext);
    }

    private void filterExistingTransactions(BatchContext context) {
        if (context.transactions.isEmpty()) return;

        List<UUID> keys = context.transactions.stream()
                .map(Transaction::getIdempotencyKey)
                .toList();

        Set<UUID> existingKeys = transactionRepository.findAllByIdempotencyKeyIn(keys).stream()
                .map(Transaction::getIdempotencyKey)
                .collect(Collectors.toSet());

        if (!existingKeys.isEmpty()) {
            List<Transaction> newTransactions = new ArrayList<>();
            List<OutboxEvent> newValidEvents = new ArrayList<>();

            for (int i = 0; i < context.transactions.size(); i++) {
                Transaction tx = context.transactions.get(i);
                OutboxEvent event = context.validEvents.get(i);

                if (existingKeys.contains(tx.getIdempotencyKey())) {
                    log.info("Transaction with idempotency key {} already exists (Found in pre-filter). Skipping.", tx.getIdempotencyKey());
                    context.processedEvents.add(event);
                } else {
                    newTransactions.add(tx);
                    newValidEvents.add(event);
                }
            }
            
            context.transactions.clear();
            context.transactions.addAll(newTransactions);
            context.validEvents.clear();
            context.validEvents.addAll(newValidEvents);
        }
    }

    private void saveTransactionsIndividually(BatchContext context) {
        List<Transaction> savedTransactions = new ArrayList<>();
        List<OutboxEvent> savedEvents = new ArrayList<>();
        
        for (int i = 0; i < context.transactions.size(); i++) {
            Transaction tx = context.transactions.get(i);
            OutboxEvent event = context.validEvents.get(i);
            
            try {
                // Double-check idempotency before saving individually
                if (transactionRepository.findByIdempotencyKey(tx.getIdempotencyKey()).isPresent()) {
                    log.warn("Transaction with idempotency key {} already exists (Found in fallback). Skipping.", tx.getIdempotencyKey());
                    context.processedEvents.add(event);
                    continue;
                }
                
                transactionRepository.save(tx);
                savedTransactions.add(tx);
                savedEvents.add(event);
            } catch (DataIntegrityViolationException ex) {
                log.error("Failed to save transaction {} individually. Reason: {}", tx.getIdempotencyKey(), ex.getMessage());
                // Mark event as processed (duplicate/invalid) to avoid infinite retry loop
                context.processedEvents.add(event);
            } catch (Exception ex) {
                log.error("Unexpected error saving transaction {}. Will retry.", tx.getIdempotencyKey(), ex);
                handleUnexpectedError(event, ex, context);
            }
        }
        
        context.transactions.clear();
        context.transactions.addAll(savedTransactions);
        context.validEvents.clear();
        context.validEvents.addAll(savedEvents);
    }

    private BatchContext prepareBatchContext(List<OutboxEvent> events) {
        var accountsMap = fetchAccounts(events);
        var context = new BatchContext(accountsMap);

        for (OutboxEvent event : events) {
            try {
                var transferData = parsePayload(event);
                var sender = accountsMap.get(transferData.senderId());
                var receiver = accountsMap.get(transferData.receiverId());

                if (sender == null || receiver == null) {
                    throw new IllegalStateException("Account not found for event " + event.getId());
                }

                var transaction = new Transaction(sender, receiver, transferData.amount(), transferData.idempotencyKey());
                context.addTransaction(event, transaction);
            } catch (Exception e) {
                handlePreparationError(event, e, context);
            }
        }
        return context;
    }

    private Map<Long, Account> fetchAccounts(List<OutboxEvent> events) {
        var accountIds = events.stream()
                .map(this::extractAccountIds)
                .flatMap(List::stream)
                .distinct()
                .toList();

        // CRITICAL FIX: Use findByIdsAndLock to prevent Lost Updates and Deadlocks
        // This ensures pessimistic locking (SELECT ... FOR UPDATE) in ID order
        return accountRepository.findByIdsAndLock(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    private List<Long> extractAccountIds(OutboxEvent event) {
        try {
            var payload = objectMapper.readTree(event.getPayload());
            return List.of(
                    payload.get("senderAccountId").asLong(),
                    payload.get("receiverAccountId").asLong()
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to parse payload for event {}.", event.getId(), e);
            return List.of();
        }
    }

    private TransferData parsePayload(OutboxEvent event) throws JsonProcessingException {
        JsonNode payload = objectMapper.readTree(event.getPayload());
        return new TransferData(
                payload.get("senderAccountId").asLong(),
                payload.get("receiverAccountId").asLong(),
                new BigDecimal(payload.get("amount").asText()),
                UUID.fromString(payload.get("idempotencyKey").asText())
        );
    }

    private void executeTransactions(BatchContext context) {
        for (int i = 0; i < context.transactions.size(); i++) {
            var transaction = context.transactions.get(i);
            var event = context.validEvents.get(i);

            try {
                processSingleTransaction(transaction);
                context.processedEvents.add(event);
            } catch (InsufficientBalanceException | IllegalStateException e) {
                handleBusinessFailure(event, transaction, e);
                context.processedEvents.add(event);
            } catch (Exception e) {
                handleUnexpectedError(event, e, context);
            }
        }
    }

    private void processSingleTransaction(Transaction transaction) {
        transaction.getSender().withdraw(transaction.getAmount());
        transaction.getReceiver().deposit(transaction.getAmount());
        transaction.complete();
        transactionAuditService.createAuditEvent(transaction, "TransactionCompleted");
    }

    private void handlePreparationError(OutboxEvent event, Exception e, BatchContext context) {
        log.error("Failed to prepare transaction for event {}. Marking as FAILED.", event.getId(), e);
        event.setStatus(OutboxEventStatus.FAILED);
        context.failedEvents.add(event);
    }

    private void handleBusinessFailure(OutboxEvent event, Transaction transaction, Exception e) {
        log.warn("Transaction for event {} FAILED. Reason: {}", event.getId(), e.getMessage());
        transaction.fail(e.getMessage());
        transactionAuditService.createAuditEvent(transaction, "TransactionFailed");
    }

    private void handleUnexpectedError(OutboxEvent event, Exception e, BatchContext context) {
        event.incrementRetryCount();
        if (event.getRetryCount() >= MAX_RETRIES) {
            log.error("Max retries reached for event {}. Marking as FAILED.", event.getId(), e);
            event.setStatus(OutboxEventStatus.FAILED);
            context.failedEvents.add(event);
        } else {
            log.warn("Retrying event {} (attempt {}/{}). Error: {}", event.getId(), event.getRetryCount(), MAX_RETRIES, e.getMessage());
            event.setStatus(OutboxEventStatus.UNPROCESSED);
            context.eventsToUpdate.add(event); // Add to update list
        }
    }

    private void persistResults(BatchContext context) {
        if (!context.accountsMap.isEmpty()) accountRepository.saveAll(context.accountsMap.values());
        if (!context.transactions.isEmpty()) transactionRepository.saveAll(context.transactions);
        if (!context.failedEvents.isEmpty()) outboxEventRepository.saveAll(context.failedEvents);
        if (!context.eventsToUpdate.isEmpty()) outboxEventRepository.saveAll(context.eventsToUpdate);
        if (!context.processedEvents.isEmpty()) outboxEventRepository.deleteAllInBatch(context.processedEvents);
    }

    // Java 21 Record for internal DTO
    private record TransferData(Long senderId, Long receiverId, BigDecimal amount, UUID idempotencyKey) {}

    // Helper class to hold batch state
    private static class BatchContext {
        final Map<Long, Account> accountsMap;
        final List<Transaction> transactions = new ArrayList<>();
        final List<OutboxEvent> validEvents = new ArrayList<>();
        final List<OutboxEvent> processedEvents = new ArrayList<>();
        final List<OutboxEvent> failedEvents = new ArrayList<>();
        final List<OutboxEvent> eventsToUpdate = new ArrayList<>(); // New list

        BatchContext(Map<Long, Account> accountsMap) {
            this.accountsMap = accountsMap;
        }

        void addTransaction(OutboxEvent event, Transaction transaction) {
            validEvents.add(event);
            transactions.add(transaction);
        }
    }
}
