package com.bss.application.service.outbox;

import com.bss.application.event.account.AccountCreatedEvent;
import com.bss.application.event.transactions.TransferRequestedEvent;
import com.bss.application.service.kafka.producer.KafkaProducerService;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.bss.domain.outbox.OutboxEventStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxProcessorService {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessorService.class);

    private static final int BATCH_SIZE = 200;
    private static final int KAFKA_TIMEOUT_SECONDS = 5;
    private static final int MAX_RETRIES = 3;

    private static final String EVENT_ACCOUNT_CREATED = "AccountCreated";
    private static final String EVENT_TRANSFER_REQUESTED = "TransferRequested";

    private static final List<String> SUPPORTED_EVENTS = List.of(
            EVENT_ACCOUNT_CREATED,
            EVENT_TRANSFER_REQUESTED
    );

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    public OutboxProcessorService(OutboxEventRepository outboxEventRepository,
                                  KafkaProducerService kafkaProducerService,
                                  ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaProducerService = kafkaProducerService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 100)
    @Transactional
    public void processOutboxEvents() {
        SUPPORTED_EVENTS.forEach(this::processBatchByType);
    }

    private void processBatchByType(String eventType) {
        List<OutboxEvent> events = fetchUnprocessedEvents(eventType);
        if (events.isEmpty()) return;

        log.debug("Processing batch of {} events for type {}", events.size(), eventType);

        var futures = dispatchEvents(events);
        
        waitForCompletion(futures);
        
        updateStatuses(events, futures);
        
        persistEvents(events);
        
        log.info("Finished processing batch of {} events for type {}", events.size(), eventType);
    }

    private List<CompletableFuture<SendResult<String, Object>>> dispatchEvents(List<OutboxEvent> events) {
        return events.stream()
                .map(this::sendToKafkaAsync)
                .toList();
    }

    private void waitForCompletion(List<CompletableFuture<SendResult<String, Object>>> futures) {
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Batch processing interrupted. Proceeding to individual checks.");
        } catch (Exception e) {
            log.warn("Batch processing incomplete or timed out. Proceeding to individual checks.");
        }
    }

    private void updateStatuses(List<OutboxEvent> events, List<CompletableFuture<SendResult<String, Object>>> futures) {
        for (int i = 0; i < events.size(); i++) {
            updateEventStatus(events.get(i), futures.get(i));
        }
    }

    private void persistEvents(List<OutboxEvent> events) {
        outboxEventRepository.saveAll(events);
    }

    private List<OutboxEvent> fetchUnprocessedEvents(String eventType) {
        LocalDateTime lockTimeout = LocalDateTime.now().minusMinutes(5);
        return outboxEventRepository.findAndLockUnprocessedEvents(
                OutboxEventStatus.UNPROCESSED,
                eventType,
                lockTimeout,
                PageRequest.of(0, BATCH_SIZE)
        );
    }

    private CompletableFuture<SendResult<String, Object>> sendToKafkaAsync(OutboxEvent event) {
        try {
            event.setLockedAt(LocalDateTime.now());
            event.setStatus(OutboxEventStatus.PROCESSING);

            return switch (event.getEventType()) {
                case EVENT_ACCOUNT_CREATED -> {
                    AccountCreatedEvent payload = objectMapper.readValue(event.getPayload(), AccountCreatedEvent.class);
                    yield kafkaProducerService.sendAccountCreatedEvent(payload);
                }
                case EVENT_TRANSFER_REQUESTED -> {
                    TransferRequestedEvent payload = objectMapper.readValue(event.getPayload(), TransferRequestedEvent.class);
                    yield kafkaProducerService.sendTransferRequestedEvent(payload);
                }
                default -> {
                    log.warn("Unknown event type: {}", event.getEventType());
                    yield CompletableFuture.failedFuture(new IllegalArgumentException("Unknown event type"));
                }
            };
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void updateEventStatus(OutboxEvent event, CompletableFuture<SendResult<String, Object>> future) {
        if (isSuccessful(future)) {
            markAsProcessed(event);
        } else {
            handleFailure(event, future);
        }
    }

    private boolean isSuccessful(CompletableFuture<SendResult<String, Object>> future) {
        return future.isDone() && !future.isCompletedExceptionally();
    }

    private void markAsProcessed(OutboxEvent event) {
        event.setStatus(OutboxEventStatus.PROCESSED);
        event.setLockedAt(null);
    }

    private void handleFailure(OutboxEvent event, CompletableFuture<SendResult<String, Object>> future) {
        Throwable cause = extractException(future);
        handleProcessingError(event, cause != null ? (Exception) cause : new RuntimeException("Unknown error"));
    }

    private Throwable extractException(CompletableFuture<?> future) {
        try {
            future.getNow(null); // Should throw if completed exceptionally
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private void handleProcessingError(OutboxEvent event, Exception e) {
        log.error("Error processing outbox event {}: {}", event.getId(), e.getMessage());
        event.handleFailure(MAX_RETRIES);
    }
}
