package com.astropay.application.scheduler;

import com.astropay.application.event.transactions.TransactionEvent;
import com.astropay.application.service.kafka.producer.KafkaProducerService;
import com.astropay.domain.model.outbox.OutboxEvent;
import com.astropay.domain.model.outbox.OutboxEventRepository;
import com.astropay.domain.model.outbox.OutboxEventStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxEventSchedulerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private KafkaProducerService kafkaProducerService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxEventScheduler scheduler;

    private TransactionEvent transactionEvent;

    @BeforeEach
    void setUp() throws Exception {
        transactionEvent = mock(TransactionEvent.class);
        when(objectMapper.readValue(anyString(), eq(TransactionEvent.class))).thenReturn(transactionEvent);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TransactionCompleted", "TransactionFailed"})
    @DisplayName("Should process notification events, send to Kafka, and delete them")
    void shouldProcessAndSendToKafka(String eventType) {
        // Arrange
        OutboxEvent event = new OutboxEvent("Transaction", "1", eventType, "{}");
        
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED), anyString(), any(Instant.class), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED), eq(eventType), any(Instant.class), any(PageRequest.class)))
                .thenReturn(List.of(event));

        // Mock Kafka success
        when(kafkaProducerService.sendTransactionEvent(any())).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        // Act
        scheduler.processNotificationEvents();

        // Assert
        verify(kafkaProducerService, times(1)).sendTransactionEvent(transactionEvent);
        verify(outboxEventRepository).deleteAllInBatch(Collections.singletonList(event));
    }

    @Test
    @DisplayName("Should mark event as FAILED after multiple Kafka send failures")
    void shouldMarkEventAsFailedAfterRetries() {
        // Arrange
        OutboxEvent event = new OutboxEvent("Transaction", "1", "TransactionCompleted", "{}");
        
        // Mock Kafka failure via CompletableFuture
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka is down"));
        when(kafkaProducerService.sendTransactionEvent(any())).thenReturn(failedFuture);
        
        event.setRetryCount(4);

        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), eq("TransactionCompleted"), any(), any()))
                .thenReturn(List.of(event));
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), eq("TransactionFailed"), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        scheduler.processNotificationEvents();

        // Assert
        verify(kafkaProducerService, times(1)).sendTransactionEvent(any());
        verify(outboxEventRepository, never()).deleteAllInBatch(any());

        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository, times(2)).saveAll(captor.capture());
        
        List<OutboxEvent> failedEventsSave = captor.getAllValues().get(1);
        assertEquals(1, failedEventsSave.size());
        assertEquals(OutboxEventStatus.FAILED, failedEventsSave.get(0).getStatus());
    }
    
    @Test
    @DisplayName("Should set event status to UNPROCESSED on single Kafka failure")
    void shouldRetryOnSingleFailure() {
        // Arrange
        OutboxEvent event = new OutboxEvent("Transaction", "1", "TransactionCompleted", "{}");
        
        // Mock Kafka failure via CompletableFuture
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka is down"));
        when(kafkaProducerService.sendTransactionEvent(any())).thenReturn(failedFuture);
        
        event.setRetryCount(0);

        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), eq("TransactionCompleted"), any(), any()))
                .thenReturn(List.of(event));
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), eq("TransactionFailed"), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        scheduler.processNotificationEvents();

        // Assert
        verify(outboxEventRepository, never()).deleteAllInBatch(any());

        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository, times(2)).saveAll(captor.capture());

        List<OutboxEvent> retryEventsSave = captor.getAllValues().get(1);

        assertEquals(1, retryEventsSave.size());
        assertEquals(OutboxEventStatus.UNPROCESSED, retryEventsSave.get(0).getStatus());
        assertEquals(1, retryEventsSave.get(0).getRetryCount());
    }
}
