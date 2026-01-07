package com.bss.application.service.outbox;

import com.bss.application.event.account.AccountCreatedEvent;
import com.bss.application.event.transactions.TransferRequestedEvent;
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
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @Mock
    private ObjectMapper objectMapper;

    private OutboxProcessorService outboxProcessorService;

    @Captor
    private ArgumentCaptor<OutboxEvent> eventCaptor;

    @BeforeEach
    void setUp() {
        outboxProcessorService = new OutboxProcessorService(
                outboxEventRepository,
                kafkaProducerService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should process AccountCreated event successfully")
    void shouldProcessAccountCreatedEventSuccessfully() throws Exception {
        // Arrange
        String payload = "{\"accountId\":1}";
        OutboxEvent event = new OutboxEvent("Account", "1", "AccountCreated", payload);
        AccountCreatedEvent eventDto = new AccountCreatedEvent(1L, 1L, "John", "john@test.com", LocalDateTime.now());

        // Mock for the target event type
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("AccountCreated"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(event));

        // Mock for other event types to return empty list (since the service iterates all types)
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("TransferRequested"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(Collections.emptyList());

        when(objectMapper.readValue(payload, AccountCreatedEvent.class)).thenReturn(eventDto);
        
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaProducerService.sendAccountCreatedEvent(eventDto)).thenReturn(future);

        // Act
        outboxProcessorService.processOutboxEvents();

        // Assert
        verify(outboxEventRepository, times(1)).saveAndFlush(event); // Lock
        verify(kafkaProducerService, times(1)).sendAccountCreatedEvent(eventDto);
        verify(outboxEventRepository, times(1)).save(event); // Mark processed

        assertEquals(OutboxEventStatus.PROCESSED, event.getStatus());
        assertNull(event.getLockedAt());
    }

    @Test
    @DisplayName("Should process TransferRequested event successfully")
    void shouldProcessTransferRequestedEventSuccessfully() throws Exception {
        // Arrange
        String payload = "{\"amount\":100}";
        OutboxEvent event = new OutboxEvent("Transfer", "uuid", "TransferRequested", payload);
        TransferRequestedEvent eventDto = mock(TransferRequestedEvent.class);

        // Mock for the target event type
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("TransferRequested"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(event));

        // Mock for other event types to return empty list (AccountCreated comes first in the loop)
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("AccountCreated"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(Collections.emptyList());

        when(objectMapper.readValue(payload, TransferRequestedEvent.class)).thenReturn(eventDto);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaProducerService.sendTransferRequestedEvent(eventDto)).thenReturn(future);

        // Act
        outboxProcessorService.processOutboxEvents();

        // Assert
        verify(kafkaProducerService, times(1)).sendTransferRequestedEvent(eventDto);
        verify(outboxEventRepository, times(1)).save(event); // Ensure it's saved as processed
        assertEquals(OutboxEventStatus.PROCESSED, event.getStatus());
    }

    @Test
    @DisplayName("Should handle Kafka processing error and increment retry count")
    void shouldHandleKafkaErrorAndRetry() throws Exception {
        // Arrange
        OutboxEvent event = new OutboxEvent("Account", "1", "AccountCreated", "{}");
        AccountCreatedEvent eventDto = mock(AccountCreatedEvent.class);

        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("AccountCreated"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(event));

        // Mock for other event types
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("TransferRequested"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(Collections.emptyList());

        when(objectMapper.readValue(anyString(), eq(AccountCreatedEvent.class))).thenReturn(eventDto);

        // Simulate Kafka Future failure
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaProducerService.sendAccountCreatedEvent(eventDto)).thenReturn(failedFuture);

        // Act
        outboxProcessorService.processOutboxEvents();

        // Assert
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent savedEvent = eventCaptor.getValue();
        
        assertEquals(OutboxEventStatus.UNPROCESSED, savedEvent.getStatus());
        assertEquals(1, savedEvent.getRetryCount());
        assertNull(savedEvent.getLockedAt());
    }

    @Test
    @DisplayName("Should mark event as FAILED after max retries")
    void shouldMarkAsFailedAfterMaxRetries() throws Exception {
        // Arrange
        OutboxEvent event = new OutboxEvent("Account", "1", "AccountCreated", "{}");
        event.setRetryCount(2); // Already retried twice. Next failure should be the 3rd and final.
        
        AccountCreatedEvent eventDto = mock(AccountCreatedEvent.class);

        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("AccountCreated"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(event));

        // Mock for other event types
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("TransferRequested"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(Collections.emptyList());

        when(objectMapper.readValue(anyString(), eq(AccountCreatedEvent.class))).thenReturn(eventDto);

        // Simulate Timeout Exception
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new TimeoutException("Timeout"));
        when(kafkaProducerService.sendAccountCreatedEvent(eventDto)).thenReturn(future);

        // Act
        outboxProcessorService.processOutboxEvents();

        // Assert
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent savedEvent = eventCaptor.getValue();

        assertEquals(OutboxEventStatus.FAILED, savedEvent.getStatus());
        assertEquals(3, savedEvent.getRetryCount());
    }

    @Test
    @DisplayName("Should handle JSON deserialization error")
    void shouldHandleJsonError() throws Exception {
        // Arrange
        OutboxEvent event = new OutboxEvent("Account", "1", "AccountCreated", "invalid-json");

        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("AccountCreated"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(event));

        // Mock for other event types
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("TransferRequested"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(Collections.emptyList());

        when(objectMapper.readValue("invalid-json", AccountCreatedEvent.class))
                .thenThrow(new JsonProcessingException("Error parsing") {});

        // Act
        outboxProcessorService.processOutboxEvents();

        // Assert
        verify(kafkaProducerService, never()).sendAccountCreatedEvent(any());
        
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent savedEvent = eventCaptor.getValue();
        
        assertEquals(OutboxEventStatus.UNPROCESSED, savedEvent.getStatus());
        assertEquals(1, savedEvent.getRetryCount());
    }

    @Test
    @DisplayName("Should mark unknown event types as PROCESSED to avoid loops")
    void shouldMarkUnknownEventTypeAsProcessed() {
        // Arrange
        OutboxEvent event = new OutboxEvent("Account", "1", "WrongType", "{}");
        
        // We force the repo to return a WrongType event even when asked for AccountCreated
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("AccountCreated"), 
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(event)); 

        // Mock for other event types
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("TransferRequested"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(Collections.emptyList());

        // Act
        outboxProcessorService.processOutboxEvents();

        // Assert
        assertEquals(OutboxEventStatus.PROCESSED, event.getStatus());
        verify(kafkaProducerService, never()).sendAccountCreatedEvent(any());
    }
    
    @Test
    @DisplayName("Should do nothing when no events found")
    void shouldDoNothingWhenNoEvents() {
        // Arrange
        // Using lenient() here because we are setting up a generic behavior for multiple calls
        // and we don't want to specify each one individually for this simple test case.
        lenient().when(outboxEventRepository.findAndLockUnprocessedEvents(
                any(), anyString(), any(), any()
        )).thenReturn(Collections.emptyList());

        // Act
        outboxProcessorService.processOutboxEvents();

        // Assert
        verify(kafkaProducerService, never()).sendAccountCreatedEvent(any());
        verify(outboxEventRepository, never()).save(any());
    }
}
