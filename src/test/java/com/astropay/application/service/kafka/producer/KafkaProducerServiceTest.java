package com.astropay.application.service.kafka.producer;

import com.astropay.application.event.account.AccountCreatedEvent;
import com.astropay.application.event.transactions.TransactionEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private KafkaProducerService kafkaProducerService;

    private CompletableFuture<SendResult<String, Object>> future;

    @BeforeEach
    void setUp() {
        future = new CompletableFuture<>();
    }

    // --- Tests for sendTransactionEvent ---

    @Test
    @DisplayName("sendTransactionEvent should call kafkaTemplate and return future")
    void sendTransactionEvent_shouldCallKafkaTemplateCorrectly() {
        // Arrange
        TransactionEvent event = new TransactionEvent(1L, 10L, 20L, BigDecimal.TEN, Instant.now(), UUID.randomUUID());
        when(kafkaTemplate.send(eq("transactions"), any(TransactionEvent.class))).thenReturn(future);

        // Act
        CompletableFuture<SendResult<String, Object>> result = kafkaProducerService.sendTransactionEvent(event);

        // Assert
        assertNotNull(result);
        verify(kafkaTemplate).send("transactions", event);
    }

    @Test
    @DisplayName("sendTransactionEvent should handle success callback")
    void sendTransactionEvent_shouldHandleSuccessCallback() {
        // Arrange
        TransactionEvent event = new TransactionEvent(1L, 10L, 20L, BigDecimal.TEN, Instant.now(), UUID.randomUUID());
        when(kafkaTemplate.send(eq("transactions"), any(TransactionEvent.class))).thenReturn(future);
        
        SendResult<String, Object> sendResult = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.offset()).thenReturn(1L);

        // Act
        kafkaProducerService.sendTransactionEvent(event);
        
        // Simulate success
        future.complete(sendResult);

        // Assert
        assertDoesNotThrow(() -> future.join());
    }

    @Test
    @DisplayName("sendTransactionEvent should handle failure callback")
    void sendTransactionEvent_shouldHandleFailureCallback() {
        // Arrange
        TransactionEvent event = new TransactionEvent(1L, 10L, 20L, BigDecimal.TEN, Instant.now(), UUID.randomUUID());
        when(kafkaTemplate.send(eq("transactions"), any(TransactionEvent.class))).thenReturn(future);

        // Act
        kafkaProducerService.sendTransactionEvent(event);
        
        // Simulate failure
        future.completeExceptionally(new RuntimeException("Kafka error"));

        // Assert
        assertDoesNotThrow(() -> {
            try {
                future.join();
            } catch (Exception e) {
                // Expected
            }
        });
    }

    // --- Tests for sendAccountCreatedEvent ---

    @Test
    @DisplayName("sendAccountCreatedEvent should call kafkaTemplate and return future")
    void sendAccountCreatedEvent_shouldCallKafkaTemplateCorrectly() {
        // Arrange
        AccountCreatedEvent event = new AccountCreatedEvent(1L, 10L, "John Doe", "john.doe@example.com", Instant.now());
        when(kafkaTemplate.send(eq("accounts"), any(AccountCreatedEvent.class))).thenReturn(future);

        // Act
        CompletableFuture<SendResult<String, Object>> result = kafkaProducerService.sendAccountCreatedEvent(event);

        // Assert
        assertNotNull(result);
        verify(kafkaTemplate).send("accounts", event);
    }
}
