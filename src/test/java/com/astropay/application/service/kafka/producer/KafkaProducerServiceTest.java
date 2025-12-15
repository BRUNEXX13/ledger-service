package com.astropay.application.service.kafka.producer;

import com.astropay.application.event.account.AccountCreatedEvent;
import com.astropay.application.event.transactions.TransactionEvent;
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
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        // This setup is flexible for both success and failure scenarios
        future = new CompletableFuture<>();
    }

    // --- Tests for sendTransactionEvent ---

    @Test
    @DisplayName("sendTransactionEvent should call kafkaTemplate with correct topic and event")
    void sendTransactionEvent_shouldCallKafkaTemplateCorrectly() {
        // Arrange
        TransactionEvent event = new TransactionEvent(1L, 10L, 20L, BigDecimal.TEN, LocalDateTime.now(), UUID.randomUUID());
        when(kafkaTemplate.send(eq("transactions"), any(TransactionEvent.class))).thenReturn(future);

        // Act
        kafkaProducerService.sendTransactionEvent(event);

        // Assert
        verify(kafkaTemplate).send("transactions", event);
    }

    // --- Tests for sendAccountCreatedEvent ---

    @Test
    @DisplayName("sendAccountCreatedEvent should call kafkaTemplate with correct topic and event")
    void sendAccountCreatedEvent_shouldCallKafkaTemplateCorrectly() {
        // Arrange
        AccountCreatedEvent event = new AccountCreatedEvent(1L, 10L, "John Doe", "john.doe@example.com", LocalDateTime.now());
        when(kafkaTemplate.send(eq("accounts"), any(AccountCreatedEvent.class))).thenReturn(future);

        // Act
        kafkaProducerService.sendAccountCreatedEvent(event);

        // Assert
        verify(kafkaTemplate).send("accounts", event);
    }
}
