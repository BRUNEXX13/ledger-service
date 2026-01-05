package com.bss.application.scheduler;

import com.bss.application.event.transactions.TransactionEvent;
import com.bss.application.service.kafka.producer.KafkaProducerService;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.bss.domain.outbox.OutboxEventStatus;
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
    private static final List<String> NOTIFICATION_EVENT_TYPES = List.of("TransactionCompleted", "TransactionFailed");

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
        LocalDateTime lockTimeout = LocalDateTime.now().minusMinutes(1);
        
        List<OutboxEvent> events = new ArrayList<>();
        for (String eventType : NOTIFICATION_EVENT_TYPES) {
            events.addAll(outboxEventRepository.findAndLockUnprocessedEvents(
                    OutboxEventStatus.UNPROCESSED, eventType, lockTimeout, PageRequest.of(0, BATCH_SIZE)));
        }

        if (events.isEmpty()) {
            return;
        }

        log.info("[Notifications] Found {} events to process.", events.size());
        LocalDateTime newLockTime = LocalDateTime.now();
        for (OutboxEvent event : events) {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setLockedAt(newLockTime);
        }
        outboxEventRepository.saveAll(events);

        List<OutboxEvent> processedEvents = new ArrayList<>();
        List<OutboxEvent> failedEvents = new ArrayList<>();
        List<OutboxEvent> eventsToRetry = new ArrayList<>(); // New list for retries

        for (OutboxEvent event : events) {
            try {
                TransactionEvent transactionEvent = objectMapper.readValue(event.getPayload(), TransactionEvent.class);
                kafkaProducerService.sendTransactionEvent(transactionEvent);
                processedEvents.add(event);
            } catch (Exception e) {
                log.error("[Notifications] Failed to send event to Kafka for outbox event id: {}. Will retry or mark as FAILED.", event.getId(), e);
                event.incrementRetryCount();
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.setStatus(OutboxEventStatus.FAILED);
                    failedEvents.add(event);
                } else {
                    event.setStatus(OutboxEventStatus.UNPROCESSED);
                    eventsToRetry.add(event); // Adds to retry list
                }
            }
        }

        if (!processedEvents.isEmpty()) {
            outboxEventRepository.deleteAllInBatch(processedEvents);
            log.info("[Notifications] Successfully processed and deleted {} events.", processedEvents.size());
        }
        if (!failedEvents.isEmpty()) {
            outboxEventRepository.saveAll(failedEvents);
            log.warn("[Notifications] Marked {} events as FAILED after multiple retries.", failedEvents.size());
        }
        if (!eventsToRetry.isEmpty()) {
            outboxEventRepository.saveAll(eventsToRetry); // Saves events for retry
            log.info("[Notifications] Marked {} events for retry.", eventsToRetry.size());
        }
    }
}
