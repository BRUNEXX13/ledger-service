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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final TransferEventScheduler self;

    public TransferEventScheduler(OutboxEventRepository outboxEventRepository,
                                  TransactionRepository transactionRepository,
                                  AccountRepository accountRepository,
                                  TransactionAuditService transactionAuditService,
                                  ObjectMapper objectMapper,
                                  @Lazy TransferEventScheduler self) {
        this.outboxEventRepository = outboxEventRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.transactionAuditService = transactionAuditService;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional(propagation = Propagation.NEVER)
    public void scheduleProcessTransferEvents() {
        self.processTransferEvents();
    }

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
        events.forEach(event -> {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setLockedAt(newLockTime);
        });
        outboxEventRepository.saveAll(events);

        try {
            processBatch(events);
        } catch (Exception e) {
            log.error("A critical error occurred during batch processing. Rolling back status for events.", e);
            events.forEach(event -> event.setStatus(OutboxEventStatus.UNPROCESSED));
            outboxEventRepository.saveAll(events);
        }
    }

    private void processBatch(List<OutboxEvent> events) {
        Map<Long, Account> accountsMap = fetchAccountsForEvents(events);
        List<Transaction> transactionsToSave = new ArrayList<>();
        List<OutboxEvent> failedEvents = new ArrayList<>();

        prepareTransactions(events, accountsMap, transactionsToSave, failedEvents);
        
        // Save initial PENDING state
        if (!transactionsToSave.isEmpty()) {
            transactionRepository.saveAll(transactionsToSave);
        }

        List<OutboxEvent> processedEvents = executeTransactions(events, transactionsToSave, failedEvents);

        persistFinalState(accountsMap, transactionsToSave, processedEvents, failedEvents);
    }

    private Map<Long, Account> fetchAccountsForEvents(List<OutboxEvent> events) {
        List<Long> accountIds = new ArrayList<>();
        for (OutboxEvent event : events) {
            try {
                JsonNode payload = objectMapper.readTree(event.getPayload());
                accountIds.add(payload.get("senderAccountId").asLong());
                accountIds.add(payload.get("receiverAccountId").asLong());
            } catch (JsonProcessingException e) {
                log.error("Failed to parse payload for event {}. Marking as FAILED.", event.getId(), e);
                event.setStatus(OutboxEventStatus.FAILED);
            }
        }
        return accountRepository.findByIds(accountIds.stream().distinct().toList())
                .stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    private void prepareTransactions(List<OutboxEvent> events, Map<Long, Account> accountsMap, 
                                     List<Transaction> transactionsToSave, List<OutboxEvent> failedEvents) {
        for (OutboxEvent event : events) {
            if (event.getStatus() == OutboxEventStatus.FAILED) continue;

            try {
                Transaction transaction = createTransactionFromEvent(event, accountsMap);
                transactionsToSave.add(transaction);
            } catch (Exception e) {
                log.error("Failed to prepare transaction for event {}. Marking as FAILED.", event.getId(), e);
                event.setStatus(OutboxEventStatus.FAILED);
                failedEvents.add(event);
            }
        }
    }

    private Transaction createTransactionFromEvent(OutboxEvent event, Map<Long, Account> accountsMap) throws JsonProcessingException {
        JsonNode payload = objectMapper.readTree(event.getPayload());
        Long senderId = payload.get("senderAccountId").asLong();
        Long receiverId = payload.get("receiverAccountId").asLong();
        BigDecimal amount = new BigDecimal(payload.get("amount").asText());
        UUID idempotencyKey = UUID.fromString(payload.get("idempotencyKey").asText());

        Account sender = accountsMap.get(senderId);
        Account receiver = accountsMap.get(receiverId);

        if (sender == null || receiver == null) {
            throw new IllegalStateException("Sender or receiver account not found for event " + event.getId());
        }

        return new Transaction(sender, receiver, amount, idempotencyKey);
    }

    private List<OutboxEvent> executeTransactions(List<OutboxEvent> events, List<Transaction> transactions, 
                                                  List<OutboxEvent> failedEvents) {
        List<OutboxEvent> processedEvents = new ArrayList<>();
        int transactionIndex = 0;

        for (OutboxEvent event : events) {
            if (event.getStatus() == OutboxEventStatus.FAILED) {
                if (!failedEvents.contains(event)) failedEvents.add(event);
                continue;
            }

            Transaction transaction = transactions.get(transactionIndex++);
            processTransactionAndHandleErrors(event, transaction, processedEvents, failedEvents);
        }
        return processedEvents;
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
        event.incrementRetryCount();
        if (event.getRetryCount() >= MAX_RETRIES) {
            log.error("Unexpected error processing event {} after {} retries. Marking as FAILED.", event.getId(), MAX_RETRIES, e);
            event.setStatus(OutboxEventStatus.FAILED);
            failedEvents.add(event);
        } else {
            log.warn("Unexpected error processing event {}. Retrying (attempt {}/{}). Error: {}", 
                     event.getId(), event.getRetryCount(), MAX_RETRIES, e.getMessage());
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
}
