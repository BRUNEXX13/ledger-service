package com.astropay.application.scheduler;

import com.astropay.application.event.transactions.TransactionEvent;
import com.astropay.application.service.kafka.producer.KafkaProducerService;
import com.astropay.domain.model.outbox.OutboxEvent;
import com.astropay.domain.model.outbox.OutboxEventRepository;
import com.astropay.domain.model.outbox.OutboxEventStatus;
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
import java.util.concurrent.CompletableFuture;

@Component
public class OutboxEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventScheduler.class);
    // TUNING: Aumentado para 500 para acompanhar a vazão de 5000 RPS
    private static final int BATCH_SIZE = 500;
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

    // TUNING: Reduzido de 3000ms para 50ms para evitar backlog massivo
    @Scheduled(fixedDelay = 50)
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

        // Log apenas se houver um volume considerável para evitar spam no log em baixa carga
        if (events.size() > 50) {
            log.info("[Notifications] Found {} events to process.", events.size());
        }
        
        LocalDateTime newLockTime = LocalDateTime.now();
        for (OutboxEvent event : events) {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setLockedAt(newLockTime);
        }
        outboxEventRepository.saveAll(events);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<OutboxEvent> processedEvents = new ArrayList<>();
        List<OutboxEvent> failedEvents = new ArrayList<>();
        List<OutboxEvent> eventsToRetry = new ArrayList<>();

        for (OutboxEvent event : events) {
            try {
                TransactionEvent transactionEvent = objectMapper.readValue(event.getPayload(), TransactionEvent.class);
                CompletableFuture<Void> future = kafkaProducerService.sendTransactionEvent(transactionEvent)
                        .thenRun(() -> processedEvents.add(event))
                        .exceptionally(ex -> {
                            log.error("[Notifications] Failed to send event to Kafka for outbox event id: {}. Will retry or mark as FAILED.", event.getId(), ex);
                            handleFailedEvent(event, failedEvents, eventsToRetry);
                            return null;
                        });
                futures.add(future);
            } catch (Exception e) {
                log.error("[Notifications] Failed to process event payload for outbox event id: {}.", event.getId(), e);
                handleFailedEvent(event, failedEvents, eventsToRetry);
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (!processedEvents.isEmpty()) {
            outboxEventRepository.deleteAllInBatch(processedEvents);
        }
        if (!failedEvents.isEmpty()) {
            outboxEventRepository.saveAll(failedEvents);
            log.warn("[Notifications] Marked {} events as FAILED after multiple retries.", failedEvents.size());
        }
        if (!eventsToRetry.isEmpty()) {
            outboxEventRepository.saveAll(eventsToRetry);
        }
    }

    private void handleFailedEvent(OutboxEvent event, List<OutboxEvent> failedEvents, List<OutboxEvent> eventsToRetry) {
        event.incrementRetryCount();
        if (event.getRetryCount() >= MAX_RETRIES) {
            event.setStatus(OutboxEventStatus.FAILED);
            failedEvents.add(event);
        } else {
            event.setStatus(OutboxEventStatus.UNPROCESSED);
            eventsToRetry.add(event);
        }
    }
}
