package com.bss.application.scheduler;

import com.bss.application.service.transaction.TransactionAuditService;
import com.bss.domain.account.Account;
import com.bss.domain.account.AccountRepository;
import com.bss.domain.account.InsufficientBalanceException;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.bss.domain.outbox.OutboxEventStatus;
import com.bss.domain.transaction.Transaction;
import com.bss.domain.transaction.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TransferEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(TransferEventScheduler.class);
    
    // Aumentado para drenar o backlog rapidamente
    private static final int BATCH_SIZE = 2000; 
    private static final int THREAD_COUNT = 8; 
    private static final int MAX_RETRIES = 5;
    private static final String IDEMPOTENCY_KEY = "idempotencyKey";

    private final OutboxEventRepository outboxEventRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionAuditService transactionAuditService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;

    public TransferEventScheduler(OutboxEventRepository outboxEventRepository,
                                  TransactionRepository transactionRepository,
                                  AccountRepository accountRepository,
                                  TransactionAuditService transactionAuditService,
                                  ObjectMapper objectMapper,
                                  PlatformTransactionManager transactionManager) {
        this.outboxEventRepository = outboxEventRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.transactionAuditService = transactionAuditService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        
        this.executorService = Executors.newFixedThreadPool(THREAD_COUNT);
    }

    // Delay reduzido para 10ms (Turbo Mode)
    @Scheduled(fixedDelay = 10)
    public void scheduleTransferProcessing() {
        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(this::processBatchInTransaction);
        }
    }

    private void processBatchInTransaction() {
        try {
            transactionTemplate.execute(status -> {
                processNextBatch();
                return null;
            });
        } catch (Exception e) {
            log.error("Error processing batch in parallel thread", e);
        }
    }

    private void processNextBatch() {
        LocalDateTime lockTimeout = LocalDateTime.now().minusMinutes(1);
        
        List<OutboxEvent> events = outboxEventRepository.findAndLockUnprocessedEvents(
                OutboxEventStatus.UNPROCESSED, "TransferRequested", lockTimeout, PageRequest.of(0, BATCH_SIZE));

        if (events.isEmpty()) {
            return;
        }

        LocalDateTime newLockTime = LocalDateTime.now();
        events.forEach(event -> {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setLockedAt(newLockTime);
        });
        outboxEventRepository.saveAll(events);

        try {
            processBatchLogic(events);
        } catch (Exception e) {
            log.error("Critical error in batch logic.", e);
            throw e; 
        }
    }

    private void processBatchLogic(List<OutboxEvent> events) {
        Map<Long, Account> accountsMap = fetchAccountsForEvents(events);
        List<Transaction> transactionsToSave = new ArrayList<>();
        List<OutboxEvent> failedEvents = new ArrayList<>();

        prepareTransactions(events, accountsMap, transactionsToSave, failedEvents);

        if (!transactionsToSave.isEmpty()) {
            transactionsToSave = saveTransactionsOrRetry(transactionsToSave, events, failedEvents);
        }

        List<OutboxEvent> processedEvents = executeTransactions(events, transactionsToSave, failedEvents);

        persistFinalState(accountsMap, transactionsToSave, processedEvents, failedEvents);
    }

    private List<Transaction> saveTransactionsOrRetry(List<Transaction> transactions, List<OutboxEvent> events, List<OutboxEvent> failedEvents) {
        try {
            return transactionRepository.saveAll(transactions);
        } catch (DataIntegrityViolationException e) {
            log.warn("Batch save failed due to data integrity violation. Retrying individually.");
            return saveTransactionsIndividually(transactions, events, failedEvents);
        }
    }

    private List<Transaction> saveTransactionsIndividually(List<Transaction> transactions, List<OutboxEvent> events, List<OutboxEvent> failedEvents) {
        List<Transaction> savedTransactions = new ArrayList<>();
        Map<UUID, OutboxEvent> eventMap = mapEventsByIdempotencyKey(events);

        for (Transaction tx : transactions) {
            try {
                savedTransactions.add(transactionRepository.save(tx));
            } catch (DataIntegrityViolationException ex) {
                log.error("Failed to save transaction individually. IdempotencyKey: {}", tx.getIdempotencyKey(), ex);
                markEventAsFailed(eventMap.get(tx.getIdempotencyKey()), failedEvents);
            }
        }
        return savedTransactions;
    }

    private List<OutboxEvent> executeTransactions(List<OutboxEvent> events, List<Transaction> transactions, List<OutboxEvent> failedEvents) {
        List<OutboxEvent> processedEvents = new ArrayList<>();
        Map<UUID, Transaction> transactionMap = mapTransactionsByIdempotencyKey(transactions);

        for (OutboxEvent event : events) {
            processEvent(event, transactionMap, processedEvents, failedEvents);
        }
        return processedEvents;
    }

    private void processEvent(OutboxEvent event, Map<UUID, Transaction> transactionMap,
                              List<OutboxEvent> processedEvents, List<OutboxEvent> failedEvents) {
        if (shouldSkipEvent(event)) return;

        UUID idempotencyKey = extractIdempotencyKey(event);
        if (idempotencyKey == null) {
            log.error("Missing idempotency key for event {}. Marking as FAILED.", event.getId());
            markEventAsFailed(event, failedEvents);
            return;
        }

        Transaction transaction = transactionMap.get(idempotencyKey);
        if (transaction == null) {
            handleMissingTransaction(event, failedEvents);
            return;
        }

        processTransactionAndHandleErrors(event, transaction, processedEvents, failedEvents);
    }

    private boolean shouldSkipEvent(OutboxEvent event) {
        return event.getStatus() == OutboxEventStatus.FAILED;
    }

    private Map<UUID, Transaction> mapTransactionsByIdempotencyKey(List<Transaction> transactions) {
        return transactions.stream()
                .collect(Collectors.toMap(Transaction::getIdempotencyKey, Function.identity()));
    }

    private void handleMissingTransaction(OutboxEvent event, List<OutboxEvent> failedEvents) {
        log.error("Transaction not found in map for event {}. Marking event as FAILED.", event.getId());
        markEventAsFailed(event, failedEvents);
    }

    private Map<UUID, OutboxEvent> mapEventsByIdempotencyKey(List<OutboxEvent> events) {
        return events.stream().collect(Collectors.toMap(
                this::extractIdempotencyKey,
                Function.identity(),
                (existing, replacement) -> existing
        ));
    }

    private UUID extractIdempotencyKey(OutboxEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            if (payload.has(IDEMPOTENCY_KEY)) {
                return UUID.fromString(payload.get(IDEMPOTENCY_KEY).asText());
            }
        } catch (JsonProcessingException | IllegalArgumentException e) {
            // Log handled elsewhere or ignored for map creation purposes
        }
        return null;
    }

    private void markEventAsFailed(OutboxEvent event, List<OutboxEvent> failedEvents) {
        if (event != null) {
            event.setStatus(OutboxEventStatus.FAILED);
            event.setRetryCount(MAX_RETRIES);
            failedEvents.add(event);
        }
    }

    private Map<Long, Account> fetchAccountsForEvents(List<OutboxEvent> events) {
        List<Long> accountIds = extractAccountIds(events);
        return fetchAndLockAccounts(accountIds);
    }

    private List<Long> extractAccountIds(List<OutboxEvent> events) {
        List<Long> accountIds = new ArrayList<>();
        for (OutboxEvent event : events) {
            extractIdsFromEvent(event, accountIds);
        }
        return accountIds;
    }

    private void extractIdsFromEvent(OutboxEvent event, List<Long> accountIds) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            accountIds.add(payload.get("senderAccountId").asLong());
            accountIds.add(payload.get("receiverAccountId").asLong());
        } catch (JsonProcessingException e) {
            log.error("Failed to parse payload for event {}. Marking as FAILED.", event.getId(), e);
            event.setStatus(OutboxEventStatus.FAILED);
        }
    }

    private Map<Long, Account> fetchAndLockAccounts(List<Long> accountIds) {
        List<Long> sortedIds = accountIds.stream().distinct().sorted().toList();

        return sortedIds.stream()
                .map(id -> accountRepository.findByIdForUpdate(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    private void prepareTransactions(List<OutboxEvent> events, Map<Long, Account> accountsMap,
                                     List<Transaction> transactionsToSave, List<OutboxEvent> failedEvents) {
        for (OutboxEvent event : events) {
            prepareTransactionForEvent(event, accountsMap, transactionsToSave, failedEvents);
        }
    }

    private void prepareTransactionForEvent(OutboxEvent event, Map<Long, Account> accountsMap,
                                            List<Transaction> transactionsToSave, List<OutboxEvent> failedEvents) {
        if (shouldSkipEvent(event)) return;

        try {
            Transaction transaction = createTransactionFromEvent(event, accountsMap);
            transactionsToSave.add(transaction);
        } catch (Exception e) {
            log.error("Failed to prepare transaction for event {}. Marking as FAILED.", event.getId(), e);
            event.setStatus(OutboxEventStatus.FAILED);
            failedEvents.add(event);
        }
    }

    private Transaction createTransactionFromEvent(OutboxEvent event, Map<Long, Account> accountsMap) throws JsonProcessingException {
        JsonNode payload = objectMapper.readTree(event.getPayload());
        Long senderId = payload.get("senderAccountId").asLong();
        Long receiverId = payload.get("receiverAccountId").asLong();
        BigDecimal amount = new BigDecimal(payload.get("amount").asText());
        UUID idempotencyKey = UUID.fromString(payload.get(IDEMPOTENCY_KEY).asText());

        Account sender = accountsMap.get(senderId);
        Account receiver = accountsMap.get(receiverId);

        if (sender == null || receiver == null) {
            throw new IllegalStateException("Sender or receiver account not found for event " + event.getId());
        }

        return new Transaction(sender, receiver, amount, idempotencyKey);
    }

    private void processTransactionAndHandleErrors(OutboxEvent event, Transaction transaction,
                                                   List<OutboxEvent> processedEvents, List<OutboxEvent> failedEvents) {
        try {
            processSingleTransaction(transaction);
            processedEvents.add(event);
        } catch (InsufficientBalanceException | IllegalStateException e) {
            handleTransactionFailure(event, transaction, e);
            processedEvents.add(event);
        } catch (Exception e) {
            handleUnexpectedError(event, e, failedEvents);
        }
    }

    private void processSingleTransaction(Transaction transaction) {
        Account sender = transaction.getSender();
        Account receiver = transaction.getReceiver();

        sender.withdraw(transaction.getAmount());
        receiver.deposit(transaction.getAmount());

        transaction.complete();
        transactionAuditService.createAuditEvent(transaction, "TransactionCompleted");
    }

    private void handleTransactionFailure(OutboxEvent event, Transaction transaction, Exception e) {
        log.warn("Transaction for event {} FAILED. Reason: {}", event.getId(), e.getMessage());
        transaction.fail(e.getMessage());
        transactionAuditService.createAuditEvent(transaction, "TransactionFailed");
    }

    private void handleUnexpectedError(OutboxEvent event, Exception e, List<OutboxEvent> failedEvents) {
        if (isDataIntegrityViolation(e)) {
            handleDataIntegrityViolation(event, failedEvents);
        } else {
            handleGenericProcessingError(event, e, failedEvents);
        }
    }

    private boolean isDataIntegrityViolation(Exception e) {
        return e instanceof DataIntegrityViolationException || e.getCause() instanceof DataIntegrityViolationException;
    }

    private void handleDataIntegrityViolation(OutboxEvent event, List<OutboxEvent> failedEvents) {
        log.warn("Duplicate transaction detected for event {}. Marking as FAILED (Idempotent).", event.getId());
        markEventAsFailed(event, failedEvents);
    }

    private void handleGenericProcessingError(OutboxEvent event, Exception e, List<OutboxEvent> failedEvents) {
        log.error("Unexpected error processing event {}. Will retry.", event.getId(), e);
        event.incrementRetryCount();
        if (event.getRetryCount() >= MAX_RETRIES) {
            markEventAsFailed(event, failedEvents);
        } else {
            event.setStatus(OutboxEventStatus.UNPROCESSED);
        }
    }

    private void persistFinalState(Map<Long, Account> accountsMap, List<Transaction> transactions,
                                   List<OutboxEvent> processedEvents, List<OutboxEvent> failedEvents) {
        saveIfNotEmpty(accountsMap.values(), accountRepository::saveAll);
        saveIfNotEmpty(transactions, transactionRepository::saveAll);
        saveIfNotEmpty(failedEvents, outboxEventRepository::saveAll);

        if (!processedEvents.isEmpty()) {
            outboxEventRepository.deleteAllInBatch(processedEvents);
        }
    }

    private <T> void saveIfNotEmpty(Collection<T> entities, Function<Collection<T>, ?> saveFunction) {
        if (!entities.isEmpty()) {
            saveFunction.apply(entities);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}
