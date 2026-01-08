package com.bss.application.service.transfer;

import com.bss.application.event.transactions.TransferRequestedEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @DisplayName("Should shutdown gracefully")
    void shutdown_shouldStopExecutor() {
        // Act
        assertDoesNotThrow(() -> transferBufferService.shutdown());
        
        // Note: We can't easily verify the internal ExecutorService state without reflection or 
        // injecting a mock ExecutorService (which would require changing the constructor signature just for tests).
        // But ensuring it doesn't throw exception is a good baseline.
    }
}
