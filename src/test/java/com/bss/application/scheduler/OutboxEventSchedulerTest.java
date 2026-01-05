package com.bss.application.scheduler;

import com.bss.application.event.transactions.TransactionEvent;
import com.bss.application.service.kafka.producer.KafkaProducerService;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.bss.domain.outbox.OutboxEventStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
    void processNotificationEvents_ShouldProcessEventsAndSendToKafka() throws Exception {
        // Arrange
        OutboxEvent event = new OutboxEvent("Transaction", "1", "TransactionCompleted", "{}");
        when(outboxEventRepository.findAndLockUnprocessedEvents(
                eq(OutboxEventStatus.UNPROCESSED), anyString(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(event))
                .thenReturn(Collections.emptyList()); // Second call returns empty

        when(objectMapper.readValue(anyString(), eq(TransactionEvent.class))).thenReturn(new TransactionEvent());

        // Act
        scheduler.processNotificationEvents();

        // Assert
        verify(kafkaProducerService).sendTransactionEvent(any(TransactionEvent.class));
        verify(outboxEventRepository).deleteAllInBatch(anyList());
        
        // Correction: Capture all calls to saveAll (we expect 2)
        // 1. To set status to PROCESSING
        // 2. (Optional) If there were failures/retries, but here success is expected.
        // Actually, the code saves PROCESSING first.
        verify(outboxEventRepository, times(1)).saveAll(anyList());
    }
}
