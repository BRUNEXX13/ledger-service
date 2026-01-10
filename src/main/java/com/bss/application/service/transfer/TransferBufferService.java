package com.bss.application.service.transfer;

import com.bss.application.event.transactions.TransferRequestedEvent;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// @Service // Desativado para arquitetura segura (Safety First)
public class TransferBufferService {

    private static final Logger log = LoggerFactory.getLogger(TransferBufferService.class);

    private static final String REDIS_MAIN_KEY = "ledger:transfer-buffer";
    private static final String REDIS_PROCESSING_PREFIX = "ledger:transfer-buffer:processing:";
    
    // Configuration Constants (Could be moved to application.properties)
    private static final int BATCH_SIZE = 2000;
    private static final int CONSUMER_THREADS = 10;
    private static final Duration BLOCKING_POP_TIMEOUT = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    
    private final ExecutorService consumerExecutor;

    public TransferBufferService(StringRedisTemplate redisTemplate,
                                 OutboxEventRepository outboxEventRepository,
                                 ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        
        // Named threads for easier debugging
        AtomicInteger threadId = new AtomicInteger(0);
        this.consumerExecutor = Executors.newFixedThreadPool(CONSUMER_THREADS, r -> {
            Thread t = new Thread(r, "transfer-consumer-" + threadId.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Enqueues a transfer request to Redis for asynchronous processing.
     * This method is on the hot path of the API.
     */
    public void enqueue(TransferRequestedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.opsForList().leftPush(REDIS_MAIN_KEY, json);
        } catch (Exception e) {
            throw new IllegalStateException("System unavailable, cannot accept transfer", e);
        }
    }

    // @PostConstruct
    public void init() {
        recoverOrphanedItems();
        startConsumers();
    }

    // @PreDestroy
    public void shutdown() {
        log.info("Shutting down transfer buffer consumers...");
        consumerExecutor.shutdownNow(); // Sends interrupt to all threads
        try {
            if (!consumerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Consumers did not terminate gracefully within 5 seconds.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void startConsumers() {
        for (int i = 0; i < CONSUMER_THREADS; i++) {
            String consumerId = "worker-" + i;
            consumerExecutor.submit(() -> runConsumerLoop(consumerId));
        }
        log.info("Started {} Redis reliable consumer threads", CONSUMER_THREADS);
    }

    private void runConsumerLoop(String consumerId) {
        String processingQueueKey = REDIS_PROCESSING_PREFIX + consumerId;
        // Reusing the list instance to reduce allocation pressure, clearing it each iteration
        List<BufferItem> batch = new ArrayList<>(BATCH_SIZE);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                consumeNextBatch(batch, processingQueueKey);
            } catch (Exception e) {
                handleConsumerException(consumerId, e);
            }
        }
    }

    private void consumeNextBatch(List<BufferItem> batch, String processingQueueKey) {
        fetchBatchFromRedis(batch, processingQueueKey);

        if (!batch.isEmpty()) {
            processBatch(batch, processingQueueKey);
        }
    }

    private void fetchBatchFromRedis(List<BufferItem> batch, String processingQueueKey) {
        while (batch.size() < BATCH_SIZE && !Thread.currentThread().isInterrupted()) {
            // Block only if batch is empty (first item)
            boolean shouldBlock = batch.isEmpty();
            
            String json = fetchItem(processingQueueKey, shouldBlock);
            
            if (json == null) {
                break; // Queue empty or timeout
            }
            
            parseAndAddToBatch(batch, json);
        }
    }

    private String fetchItem(String processingQueueKey, boolean shouldBlock) {
        if (shouldBlock) {
            return redisTemplate.opsForList().rightPopAndLeftPush(REDIS_MAIN_KEY, processingQueueKey, BLOCKING_POP_TIMEOUT);
        }
        return redisTemplate.opsForList().rightPopAndLeftPush(REDIS_MAIN_KEY, processingQueueKey);
    }

    private void parseAndAddToBatch(List<BufferItem> batch, String json) {
        try {
            // We deserialize to get the ID, but we keep the raw JSON for the DB payload.
            // This avoids re-serializing the object later (CPU Optimization).
            TransferRequestedEvent event = objectMapper.readValue(json, TransferRequestedEvent.class);
            batch.add(new BufferItem(event, json));
        } catch (JsonProcessingException e) {
            log.error("Error deserializing event from Redis. Skipping item.", e);
        }
    }

    private void processBatch(List<BufferItem> batch, String processingQueueKey) {
        persistToDatabase(batch);
        redisTemplate.delete(processingQueueKey); // Ack
        batch.clear();
    }

    private void persistToDatabase(List<BufferItem> items) {
        List<OutboxEvent> outboxEvents = items.stream()
                .map(item -> new OutboxEvent(
                        "Transfer",
                        item.event().idempotencyKey().toString(),
                        "TransferRequested",
                        item.rawJson() // Use the original JSON string!
                ))
                .toList();

        if (!outboxEvents.isEmpty()) {
            outboxEventRepository.saveAll(outboxEvents);
        }
    }

    private void recoverOrphanedItems() {
        try {
            // Note: 'keys' command is blocking. Safe here due to low cardinality of keys,
            // but in large clusters prefer 'scan'.
            Set<String> processingKeys = redisTemplate.keys(REDIS_PROCESSING_PREFIX + "*");
            if (processingKeys != null && !processingKeys.isEmpty()) {
                log.info("Found {} orphaned processing queues. Recovering items...", processingKeys.size());
                processingKeys.forEach(this::recoverQueue);
                log.info("Recovery complete.");
            }
        } catch (Exception e) {
            log.error("Failed to recover orphaned items", e);
        }
    }

    private void recoverQueue(String key) {
        String item;
        do {
            item = redisTemplate.opsForList().rightPopAndLeftPush(key, REDIS_MAIN_KEY);
        } while (item != null);
        redisTemplate.delete(key);
    }

    private void handleConsumerException(String consumerId, Exception e) {
        if (e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
            log.info("Consumer {} interrupted, stopping.", consumerId);
            Thread.currentThread().interrupt();
        } else {
            log.error("Error in Redis consumer loop {}", consumerId, e);
            sleepBriefly();
        }
    }

    private void sleepBriefly() {
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // Internal record to hold both the parsed object (for ID) and raw JSON (for DB)
    private record BufferItem(TransferRequestedEvent event, String rawJson) {}
}
