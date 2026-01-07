package com.bss.application.scheduler;

import com.bss.application.event.account.AccountCreatedEvent;
import com.bss.application.event.transactions.TransactionEvent;
import com.bss.application.service.kafka.producer.KafkaProducerService;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.bss.domain.outbox.OutboxEventStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventSchedulerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @Mock
    private ObjectMapper objectMapper;

    private OutboxEventScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxEventScheduler(outboxEventRepository, kafkaProducerService, objectMapper);
    }

    @Test
    @DisplayName("Should process TransactionCompleted events successfully")
    void shouldProcessTransactionCompletedEvents() throws Exception {
        // Arrange
        OutboxEvent event = new OutboxEvent("Transaction", "1", "TransactionCompleted", "{}");
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED), eq("TransactionCompleted"), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED), eq("TransactionFailed"), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED), eq("AccountCreated"), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        when(objectMapper.readValue(anyString(), eq(TransactionEvent.class))).thenReturn(new TransactionEvent());

        // Act
        scheduler.processNotificationEvents();

        // Assert
        verify(kafkaProducerService).sendTransactionEvent(any(TransactionEvent.class));
        verify(outboxEventRepository).deleteAllInBatch(anyList());
        
        // Verify status update to PROCESSING
        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository).saveAll(captor.capture());
        assertEquals(OutboxEventStatus.PROCESSING, captor.getValue().get(0).getStatus());
    }

    @Test
    @DisplayName("Should process AccountCreated events successfully")
    void shouldProcessAccountCreatedEvents() throws Exception {
        // Arrange
        OutboxEvent event = new OutboxEvent("Account", "1", "AccountCreated", "{}");
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED), eq("TransactionCompleted"), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED), eq("TransactionFailed"), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED), eq("AccountCreated"), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(event));

        when(objectMapper.readValue(anyString(), eq(AccountCreatedEvent.class))).thenReturn(new AccountCreatedEvent());

        // Act
        scheduler.processNotificationEvents();

        // Assert
        verify(kafkaProducerService).sendAccountCreatedEvent(any(AccountCreatedEvent.class));
        verify(outboxEventRepository).deleteAllInBatch(anyList());
    }

    @Test
    @DisplayName("Should handle deserialization error and retry")
    void shouldHandleDeserializationError() throws Exception {
        // Arrange
        OutboxEvent event = new OutboxEvent("Transaction", "1", "TransactionCompleted", "invalid-json");
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED), eq("TransactionCompleted"), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(event));
        
        when(objectMapper.readValue(anyString(), eq(TransactionEvent.class))).thenThrow(new JsonProcessingException("Error") {});

        // Act
        scheduler.processNotificationEvents();

        // Assert
        verify(kafkaProducerService, never()).sendTransactionEvent(any());
        verify(outboxEventRepository, never()).deleteAllInBatch(any());
        
        // Verify retry logic
        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository, times(2)).saveAll(captor.capture()); // 1: PROCESSING, 2: UNPROCESSED (Retry)
        
        List<OutboxEvent> retriedEvents = captor.getAllValues().get(1);
        assertEquals(1, retriedEvents.size());
        assertEquals(OutboxEventStatus.UNPROCESSED, retriedEvents.get(0).getStatus());
        assertEquals(1, retriedEvents.get(0).getRetryCount());
    }

    @Test
    @DisplayName("Should mark as FAILED after max retries")
    void shouldMarkAsFailedAfterMaxRetries() throws Exception {
        // Arrange
        OutboxEvent event = new OutboxEvent("Transaction", "1", "TransactionCompleted", "{}");
        event.setRetryCount(4); // Max retries is 5, so next failure should fail it
        
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED), eq("TransactionCompleted"), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(event));
        
        doThrow(new RuntimeException("Kafka error")).when(kafkaProducerService).sendTransactionEvent(any());
        when(objectMapper.readValue(anyString(), eq(TransactionEvent.class))).thenReturn(new TransactionEvent());

        // Act
        scheduler.processNotificationEvents();

        // Assert
        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository, times(2)).saveAll(captor.capture());
        
        List<OutboxEvent> failedEvents = captor.getAllValues().get(1);
        assertEquals(OutboxEventStatus.FAILED, failedEvents.get(0).getStatus());
    }

    @Test
    @DisplayName("Should do nothing if no events found")
    void shouldDoNothingIfNoEvents() {
        // Arrange
        when(outboxEventRepository.findAndLockUnprocessedEvents(any(), any(), any(), any())).thenReturn(Collections.emptyList());

        // Act
        scheduler.processNotificationEvents();

        // Assert
        verify(outboxEventRepository, never()).saveAll(any());
        verify(kafkaProducerService, never()).sendTransactionEvent(any());
    }
}
