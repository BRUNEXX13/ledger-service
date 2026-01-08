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
    private ArgumentCaptor<List<OutboxEvent>> eventsCaptor;

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

        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("AccountCreated"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(event));

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
        verify(kafkaProducerService, times(1)).sendAccountCreatedEvent(eventDto);
        verify(outboxEventRepository).saveAll(eventsCaptor.capture());
        
        OutboxEvent savedEvent = eventsCaptor.getValue().get(0);
        assertEquals(OutboxEventStatus.PROCESSED, savedEvent.getStatus());
        assertNull(savedEvent.getLockedAt());
    }

    @Test
    @DisplayName("Should process TransferRequested event successfully")
    void shouldProcessTransferRequestedEventSuccessfully() throws Exception {
        // Arrange
        String payload = "{\"amount\":100}";
        OutboxEvent event = new OutboxEvent("Transfer", "uuid", "TransferRequested", payload);
        TransferRequestedEvent eventDto = mock(TransferRequestedEvent.class);

        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("TransferRequested"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(event));

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
        verify(outboxEventRepository).saveAll(eventsCaptor.capture());
        assertEquals(OutboxEventStatus.PROCESSED, eventsCaptor.getValue().get(0).getStatus());
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
        verify(outboxEventRepository).saveAll(eventsCaptor.capture());
        OutboxEvent savedEvent = eventsCaptor.getValue().get(0);
        
        assertEquals(OutboxEventStatus.UNPROCESSED, savedEvent.getStatus());
        assertEquals(1, savedEvent.getRetryCount());
        assertNull(savedEvent.getLockedAt());
    }

    @Test
    @DisplayName("Should mark event as FAILED after max retries")
    void shouldMarkAsFailedAfterMaxRetries() throws Exception {
        // Arrange
        OutboxEvent event = new OutboxEvent("Account", "1", "AccountCreated", "{}");
        event.setRetryCount(2); 
        
        AccountCreatedEvent eventDto = mock(AccountCreatedEvent.class);

        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("AccountCreated"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(event));

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
        verify(outboxEventRepository).saveAll(eventsCaptor.capture());
        OutboxEvent savedEvent = eventsCaptor.getValue().get(0);

        assertEquals(OutboxEventStatus.FAILED, savedEvent.getStatus());
        assertEquals(3, savedEvent.getRetryCount());
    }

    @Test
    @DisplayName("Should handle unknown error (fallback)")
    void shouldHandleUnknownError() throws Exception {
        // Arrange
        OutboxEvent event = new OutboxEvent("Account", "1", "AccountCreated", "{}");
        AccountCreatedEvent eventDto = mock(AccountCreatedEvent.class);

        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("AccountCreated"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(event));

        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED),
                eq("TransferRequested"),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(Collections.emptyList());

        when(objectMapper.readValue(anyString(), eq(AccountCreatedEvent.class))).thenReturn(eventDto);

        // Simulate a Future that completes exceptionally with null (should trigger fallback)
        // Or a future that is cancelled
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.cancel(true);
        when(kafkaProducerService.sendAccountCreatedEvent(eventDto)).thenReturn(future);

        // Act
        outboxProcessorService.processOutboxEvents();

        // Assert
        verify(outboxEventRepository).saveAll(eventsCaptor.capture());
        OutboxEvent savedEvent = eventsCaptor.getValue().get(0);
        
        assertEquals(OutboxEventStatus.UNPROCESSED, savedEvent.getStatus());
        assertEquals(1, savedEvent.getRetryCount());
    }
}
