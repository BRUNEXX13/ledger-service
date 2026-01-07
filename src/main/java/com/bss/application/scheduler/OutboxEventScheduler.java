package com.bss.application.scheduler;

import com.bss.application.event.account.AccountCreatedEvent;
import com.bss.application.event.transactions.TransactionEvent;
import com.bss.application.service.kafka.producer.KafkaProducerService;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.bss.domain.outbox.OutboxEventStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class OutboxEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventScheduler.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 5;
    private static final String EVENT_ACCOUNT_CREATED = "AccountCreated";
    private static final List<String> NOTIFICATION_EVENT_TYPES = List.of("TransactionCompleted", "TransactionFailed", EVENT_ACCOUNT_CREATED);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    public OutboxEventScheduler(OutboxEventRepository outboxEventRepository,
                                KafkaProducerService kafkaProducerService,
                                ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaProducerService = kafkaProducerService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void processNotificationEvents() {
        List<OutboxEvent> events = fetchAndLockEvents();

        if (events.isEmpty()) {
            return;
        }

        log.info("[Notifications] Found {} events to process.", events.size());
        processEvents(events);
    }

    private List<OutboxEvent> fetchAndLockEvents() {
        List<OutboxEvent> events = fetchUnprocessedEvents();
        lockEvents(events);
        return events;
    }

    private List<OutboxEvent> fetchUnprocessedEvents() {
        LocalDateTime lockTimeout = LocalDateTime.now().minusMinutes(1);
        List<OutboxEvent> events = new ArrayList<>();
        
        for (String eventType : NOTIFICATION_EVENT_TYPES) {
            if (isBatchFull(events)) {
                break;
            }
            fetchEventsByType(eventType, lockTimeout, events);
        }
        return events;
    }

    private boolean isBatchFull(List<OutboxEvent> events) {
        return events.size() >= BATCH_SIZE;
    }

    private void fetchEventsByType(String eventType, LocalDateTime lockTimeout, List<OutboxEvent> events) {
        int remainingBatchSize = BATCH_SIZE - events.size();
        if (remainingBatchSize <= 0) {
            return;
        }
        
        List<OutboxEvent> typeEvents = outboxEventRepository.findAndLockUnprocessedEvents(
                OutboxEventStatus.UNPROCESSED, eventType, lockTimeout, PageRequest.of(0, remainingBatchSize));
        
        events.addAll(typeEvents);
    }

    private void lockEvents(List<OutboxEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        LocalDateTime newLockTime = LocalDateTime.now();
        events.forEach(event -> {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setLockedAt(newLockTime);
        });
        outboxEventRepository.saveAll(events);
    }

    private void processEvents(List<OutboxEvent> events) {
        ProcessingResult result = new ProcessingResult();

        for (OutboxEvent event : events) {
            processSingleEvent(event, result);
        }

        persistResults(result);
    }

    private void processSingleEvent(OutboxEvent event, ProcessingResult result) {
        try {
            sendEventToKafka(event);
            result.addProcessed(event);
        } catch (Exception e) {
            handleProcessingError(event, e, result);
        }
    }

    private void sendEventToKafka(OutboxEvent event) throws JsonProcessingException {
        if (EVENT_ACCOUNT_CREATED.equals(event.getEventType())) {
            AccountCreatedEvent accountEvent = objectMapper.readValue(event.getPayload(), AccountCreatedEvent.class);
            kafkaProducerService.sendAccountCreatedEvent(accountEvent);
        } else {
            TransactionEvent transactionEvent = objectMapper.readValue(event.getPayload(), TransactionEvent.class);
            kafkaProducerService.sendTransactionEvent(transactionEvent);
        }
    }

    private void handleProcessingError(OutboxEvent event, Exception e, ProcessingResult result) {
        log.error("[Notifications] Failed to send event to Kafka for outbox event id: {}. Will retry or mark as FAILED.", event.getId(), e);
        event.incrementRetryCount();
        
        if (event.getRetryCount() >= MAX_RETRIES) {
            event.setStatus(OutboxEventStatus.FAILED);
            result.addFailed(event);
        } else {
            event.setStatus(OutboxEventStatus.UNPROCESSED);
            result.addRetry(event);
        }
    }

    private void persistResults(ProcessingResult result) {
        persistProcessedEvents(result);
        persistFailedEvents(result);
        persistRetryEvents(result);
    }

    private void persistProcessedEvents(ProcessingResult result) {
        if (result.hasProcessed()) {
            outboxEventRepository.deleteAllInBatch(result.getProcessedEvents());
            log.info("[Notifications] Successfully processed and deleted {} events.", result.getProcessedCount());
        }
    }

    private void persistFailedEvents(ProcessingResult result) {
        if (result.hasFailed()) {
            outboxEventRepository.saveAll(result.getFailedEvents());
            log.warn("[Notifications] Marked {} events as FAILED after multiple retries.", result.getFailedCount());
        }
    }

    private void persistRetryEvents(ProcessingResult result) {
        if (result.hasRetries()) {
            outboxEventRepository.saveAll(result.getEventsToRetry());
            log.info("[Notifications] Marked {} events for retry.", result.getRetryCount());
        }
    }

    // Inner class to hold processing results, avoiding multiple list arguments
    private static class ProcessingResult {
        private final List<OutboxEvent> processedEvents = new ArrayList<>();
        private final List<OutboxEvent> failedEvents = new ArrayList<>();
        private final List<OutboxEvent> eventsToRetry = new ArrayList<>();

        void addProcessed(OutboxEvent event) { processedEvents.add(event); }
        void addFailed(OutboxEvent event) { failedEvents.add(event); }
        void addRetry(OutboxEvent event) { eventsToRetry.add(event); }

        List<OutboxEvent> getProcessedEvents() { return processedEvents; }
        List<OutboxEvent> getFailedEvents() { return failedEvents; }
        List<OutboxEvent> getEventsToRetry() { return eventsToRetry; }

        boolean hasProcessed() { return !processedEvents.isEmpty(); }
        boolean hasFailed() { return !failedEvents.isEmpty(); }
        boolean hasRetries() { return !eventsToRetry.isEmpty(); }

        int getProcessedCount() { return processedEvents.size(); }
        int getFailedCount() { return failedEvents.size(); }
        int getRetryCount() { return eventsToRetry.size(); }
    }
}
