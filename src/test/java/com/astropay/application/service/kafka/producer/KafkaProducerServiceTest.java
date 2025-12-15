package com.astropay.application.service.kafka.producer;

import com.astropay.application.event.account.AccountCreatedEvent;
import com.astropay.application.event.transactions.TransactionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private KafkaProducerService kafkaProducerService;

    @BeforeEach
    void setUp() {
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), any())).thenReturn(future);
    }

    @Test
    @DisplayName("Should send transaction event to 'transactions' topic")
    void shouldSendTransactionEvent() {
        // Arrange
        TransactionEvent event = mock(TransactionEvent.class);
        when(event.getIdempotencyKey()).thenReturn(UUID.randomUUID());

        // Act
        kafkaProducerService.sendTransactionEvent(event);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TransactionEvent> eventCaptor = ArgumentCaptor.forClass(TransactionEvent.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());

        assertEquals("transactions", topicCaptor.getValue());
        assertEquals(event, eventCaptor.getValue());
    }

    @Test
    @DisplayName("Should send account created event to 'accounts' topic")
    void shouldSendAccountCreatedEvent() {
        // Arrange
        AccountCreatedEvent event = mock(AccountCreatedEvent.class);
        when(event.getAccountId()).thenReturn(1L);

        // Act
        kafkaProducerService.sendAccountCreatedEvent(event);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AccountCreatedEvent> eventCaptor = ArgumentCaptor.forClass(AccountCreatedEvent.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());

        assertEquals("accounts", topicCaptor.getValue());
        assertEquals(event, eventCaptor.getValue());
    }
}
