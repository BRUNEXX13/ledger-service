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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class OutboxProcessorService {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessorService.class);
    
    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 3;
    private static final int KAFKA_TIMEOUT_SECONDS = 5;
    
    // Event Type Constants
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

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void processOutboxEvents() {
        SUPPORTED_EVENTS.forEach(this::processBatchByType);
    }

    private void processBatchByType(String eventType) {
        List<OutboxEvent> events = fetchUnprocessedEvents(eventType);

        if (events.isEmpty()) {
            return;
        }

        log.info("Found {} unprocessed outbox events of type {}", events.size(), eventType);
        events.forEach(this::processEventLifecycle);
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

    /**
     * Manages the lifecycle of a single event: Lock -> Send -> Complete/Fail
     */
    private void processEventLifecycle(OutboxEvent event) {
        try {
            lockEvent(event);
            
            publishToKafka(event);
            
            markAsProcessed(event);
        } catch (Exception e) {
            handleProcessingError(event, e);
        }
    }

    private void lockEvent(OutboxEvent event) {
        event.setLockedAt(LocalDateTime.now());
        event.setStatus(OutboxEventStatus.PROCESSING);
        outboxEventRepository.saveAndFlush(event);
    }

    private void markAsProcessed(OutboxEvent event) {
        event.setStatus(OutboxEventStatus.PROCESSED);
        event.setLockedAt(null);
        outboxEventRepository.save(event);
        log.info("Successfully processed outbox event {}", event.getId());
    }

    /**
     * Routes the event to the correct Kafka producer based on its type.
     * Waits for acknowledgment (Sync over Async).
     */
    private void publishToKafka(OutboxEvent event) throws Exception {
        switch (event.getEventType()) {
            case EVENT_ACCOUNT_CREATED -> handleAccountCreated(event);
            case EVENT_TRANSFER_REQUESTED -> handleTransferRequested(event);
            default -> log.warn("Unknown event type: {}", event.getEventType());
        }
    }

    private void handleAccountCreated(OutboxEvent event) throws JsonProcessingException, ExecutionException, InterruptedException, TimeoutException {
        AccountCreatedEvent payload = objectMapper.readValue(event.getPayload(), AccountCreatedEvent.class);
        kafkaProducerService.sendAccountCreatedEvent(payload).get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void handleTransferRequested(OutboxEvent event) throws JsonProcessingException, ExecutionException, InterruptedException, TimeoutException {
        TransferRequestedEvent payload = objectMapper.readValue(event.getPayload(), TransferRequestedEvent.class);
        kafkaProducerService.sendTransferRequestedEvent(payload).get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void handleProcessingError(OutboxEvent event, Exception e) {
        log.error("Error processing outbox event {}: {}", event.getId(), e.getMessage());
        
        event.incrementRetryCount();
        event.setLockedAt(null);
        
        if (event.getRetryCount() >= MAX_RETRIES) {
            event.setStatus(OutboxEventStatus.FAILED);
            log.error("Outbox event {} failed after {} retries", event.getId(), MAX_RETRIES);
        } else {
            event.setStatus(OutboxEventStatus.UNPROCESSED);
        }

        outboxEventRepository.save(event);
    }
}
