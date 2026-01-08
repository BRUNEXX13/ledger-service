package com.bss.application.service.transfer;

import com.bss.application.event.transactions.TransferRequestedEvent;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferBufferServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    private TransferBufferService transferBufferService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        transferBufferService = new TransferBufferService(redisTemplate, outboxEventRepository, objectMapper);
    }

    @Test
    @DisplayName("Should enqueue event to Redis successfully")
    void enqueue_shouldPushToRedis() throws JsonProcessingException {
        // Arrange
        TransferRequestedEvent event = new TransferRequestedEvent(1L, 2L, BigDecimal.TEN, UUID.randomUUID());
        String json = "{\"senderAccountId\":1}";
        
        when(objectMapper.writeValueAsString(event)).thenReturn(json);

        // Act
        transferBufferService.enqueue(event);

        // Assert
        verify(listOperations).leftPush(eq("ledger:transfer-buffer"), eq(json));
    }

    @Test
    @DisplayName("Should throw exception when Redis is unavailable during enqueue")
    void enqueue_shouldThrowException_whenRedisFails() throws JsonProcessingException {
        // Arrange
        TransferRequestedEvent event = new TransferRequestedEvent(1L, 2L, BigDecimal.TEN, UUID.randomUUID());
        when(objectMapper.writeValueAsString(event)).thenReturn("{}");
        doThrow(new RuntimeException("Redis down")).when(listOperations).leftPush(anyString(), anyString());

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> transferBufferService.enqueue(event));
    }

    @Test
    @DisplayName("Should throw exception on serialization error during enqueue")
    void enqueue_shouldThrowException_onSerializationError() throws JsonProcessingException {
        // Arrange
        TransferRequestedEvent event = new TransferRequestedEvent(1L, 2L, BigDecimal.TEN, UUID.randomUUID());
        when(objectMapper.writeValueAsString(event)).thenThrow(new JsonProcessingException("Error") {});

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> transferBufferService.enqueue(event));
    }

    @Test
    @DisplayName("Should recover orphaned items on init")
    void init_shouldRecoverOrphanedItems() {
        // Arrange
        String processingKey = "ledger:transfer-buffer:processing:worker-1";
        when(redisTemplate.keys(anyString())).thenReturn(Set.of(processingKey));
        
        // Simulate one item in the orphaned queue, then null (empty)
        when(listOperations.rightPopAndLeftPush(eq(processingKey), eq("ledger:transfer-buffer")))
                .thenReturn("item1")
                .thenReturn(null);

        // Act
        transferBufferService.init();

        // Assert
        // Should have moved item1 back to main queue
        verify(listOperations, times(2)).rightPopAndLeftPush(eq(processingKey), eq("ledger:transfer-buffer"));
        // Should have deleted the empty processing key
        verify(redisTemplate).delete(processingKey);
    }

    @Test
    @DisplayName("Should handle empty orphaned keys gracefully")
    void init_shouldHandleNoOrphanedItems() {
        // Arrange
        when(redisTemplate.keys(anyString())).thenReturn(Collections.emptySet());

        // Act
        transferBufferService.init();

        // Assert
        verify(listOperations, never()).rightPopAndLeftPush(anyString(), anyString());
    }

    @Test
    @DisplayName("Should start consumers on init")
    void init_shouldStartConsumers() {
        // Arrange
        ExecutorService mockExecutor = mock(ExecutorService.class);
        // Inject mock executor to verify task submission
        ReflectionTestUtils.setField(transferBufferService, "consumerExecutor", mockExecutor);
        
        when(redisTemplate.keys(anyString())).thenReturn(Collections.emptySet());

        // Act
        transferBufferService.init();

        // Assert
        // Should submit 10 tasks (CONSUMER_THREADS = 10)
        verify(mockExecutor, times(10)).submit(any(Runnable.class));
    }

    @Test
    @DisplayName("Should shutdown gracefully")
    void shutdown_shouldStopExecutor() {
        // Act
        assertDoesNotThrow(() -> transferBufferService.shutdown());
    }

    @Test
    @DisplayName("Should process batch correctly: fetch from Redis, save to DB, and ack")
    void consumeNextBatch_shouldProcessBatchAndAck() throws JsonProcessingException {
        // Arrange
        String processingKey = "ledger:transfer-buffer:processing:test-worker";
        String json1 = "{\"idempotencyKey\":\"uuid-1\"}";
        String json2 = "{\"idempotencyKey\":\"uuid-2\"}";
        TransferRequestedEvent event1 = mock(TransferRequestedEvent.class);
        TransferRequestedEvent event2 = mock(TransferRequestedEvent.class);
        
        when(event1.idempotencyKey()).thenReturn(UUID.randomUUID());
        when(event2.idempotencyKey()).thenReturn(UUID.randomUUID());

        // Mock Redis fetch: return 2 items then null (empty)
        when(listOperations.rightPopAndLeftPush(eq("ledger:transfer-buffer"), eq(processingKey), any(Duration.class)))
                .thenReturn(json1); // First blocking call
        when(listOperations.rightPopAndLeftPush(eq("ledger:transfer-buffer"), eq(processingKey)))
                .thenReturn(json2)  // Second non-blocking call
                .thenReturn(null);  // Third call returns null, stopping the batch

        // Mock Deserialization
        when(objectMapper.readValue(json1, TransferRequestedEvent.class)).thenReturn(event1);
        when(objectMapper.readValue(json2, TransferRequestedEvent.class)).thenReturn(event2);

        // Prepare batch list to be passed to private method
        List<Object> batch = new ArrayList<>();

        // Act
        // Invoke private method consumeNextBatch(List<BufferItem> batch, String processingQueueKey)
        ReflectionTestUtils.invokeMethod(transferBufferService, "consumeNextBatch", batch, processingKey);

        // Assert
        // 1. Verify DB persistence
        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository).saveAll(captor.capture());
        List<OutboxEvent> savedEvents = captor.getValue();
        assertEquals(2, savedEvents.size());
        assertEquals(json1, savedEvents.get(0).getPayload()); // Should use raw JSON
        assertEquals(json2, savedEvents.get(1).getPayload());

        // 2. Verify Redis Ack (delete processing key)
        verify(redisTemplate).delete(processingKey);
        
        // 3. Verify batch is cleared
        assertTrue(batch.isEmpty());
    }

    @Test
    @DisplayName("Should handle deserialization error by skipping item but processing rest of batch")
    void consumeNextBatch_shouldHandleDeserializationError() throws JsonProcessingException {
        // Arrange
        String processingKey = "ledger:transfer-buffer:processing:test-worker";
        String validJson = "{\"idempotencyKey\":\"uuid-1\"}";
        String invalidJson = "invalid-json";
        TransferRequestedEvent event1 = mock(TransferRequestedEvent.class);
        when(event1.idempotencyKey()).thenReturn(UUID.randomUUID());

        // Mock Redis fetch: valid, invalid, null
        when(listOperations.rightPopAndLeftPush(eq("ledger:transfer-buffer"), eq(processingKey), any(Duration.class)))
                .thenReturn(validJson);
        when(listOperations.rightPopAndLeftPush(eq("ledger:transfer-buffer"), eq(processingKey)))
                .thenReturn(invalidJson)
                .thenReturn(null);

        // Mock Deserialization
        when(objectMapper.readValue(validJson, TransferRequestedEvent.class)).thenReturn(event1);
        when(objectMapper.readValue(invalidJson, TransferRequestedEvent.class)).thenThrow(new JsonProcessingException("Parse error") {});

        List<Object> batch = new ArrayList<>();

        // Act
        ReflectionTestUtils.invokeMethod(transferBufferService, "consumeNextBatch", batch, processingKey);

        // Assert
        // Should save only the valid event
        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository).saveAll(captor.capture());
        List<OutboxEvent> savedEvents = captor.getValue();
        assertEquals(1, savedEvents.size());
        assertEquals(validJson, savedEvents.get(0).getPayload());

        // Should still ack the batch (including the skipped item effectively, as the key is deleted)
        verify(redisTemplate).delete(processingKey);
    }

    @Test
    @DisplayName("Should do nothing if queue is empty")
    void consumeNextBatch_shouldDoNothingIfEmpty() {
        // Arrange
        String processingKey = "ledger:transfer-buffer:processing:test-worker";
        
        // Mock Redis fetch returning null immediately
        when(listOperations.rightPopAndLeftPush(eq("ledger:transfer-buffer"), eq(processingKey), any(Duration.class)))
                .thenReturn(null);

        List<Object> batch = new ArrayList<>();

        // Act
        ReflectionTestUtils.invokeMethod(transferBufferService, "consumeNextBatch", batch, processingKey);

        // Assert
        verify(outboxEventRepository, never()).saveAll(any());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("fetchItem should use blocking pop when shouldBlock is true")
    void fetchItem_shouldBlock() {
        // Act
        ReflectionTestUtils.invokeMethod(transferBufferService, "fetchItem", "key", true);
        
        // Assert
        verify(listOperations).rightPopAndLeftPush(eq("ledger:transfer-buffer"), eq("key"), any(Duration.class));
    }

    @Test
    @DisplayName("fetchItem should use non-blocking pop when shouldBlock is false")
    void fetchItem_shouldNotBlock() {
        // Act
        ReflectionTestUtils.invokeMethod(transferBufferService, "fetchItem", "key", false);
        
        // Assert
        verify(listOperations).rightPopAndLeftPush(eq("ledger:transfer-buffer"), eq("key"));
    }

    @Test
    @DisplayName("Should preserve interrupt status on InterruptedException")
    void handleConsumerException_shouldPreserveInterruptStatus() {
        // Act
        ReflectionTestUtils.invokeMethod(transferBufferService, "handleConsumerException", "test-worker", new InterruptedException());

        // Assert
        assertTrue(Thread.currentThread().isInterrupted());
        
        // Cleanup: Clear the interrupt flag to not affect other tests
        Thread.interrupted();
    }
}
