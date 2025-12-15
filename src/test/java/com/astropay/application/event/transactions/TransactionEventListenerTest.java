package com.astropay.application.event.transactions;

import com.astropay.application.service.notification.EmailService;
import com.astropay.domain.model.user.User;
import com.astropay.domain.model.user.UserRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionEventListenerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TransactionEventListener eventListener;

    @Test
    @DisplayName("Should handle transaction event and send emails to sender and receiver")
    void shouldHandleTransactionEvent() {
        // Arrange
        BigDecimal amount = new BigDecimal("100.50");
        TransactionEvent event = new TransactionEvent(1L, 10L, 20L, amount, null, UUID.randomUUID());
        ConsumerRecord<String, TransactionEvent> record = new ConsumerRecord<>("transactions", 0, 0, "key", event);

        User sender = mock(User.class);
        when(sender.getName()).thenReturn("Sender Name");
        when(sender.getEmail()).thenReturn("sender@example.com");

        User receiver = mock(User.class);
        when(receiver.getName()).thenReturn("Receiver Name");
        when(receiver.getEmail()).thenReturn("receiver@example.com");

        when(userRepository.findById(10L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(20L)).thenReturn(Optional.of(receiver));

        // Act
        eventListener.handleTransactionEvent(record);

        // Assert
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).sendTransactionNotification(emailCaptor.capture(), anyString(), bodyCaptor.capture());

        List<String> emails = emailCaptor.getAllValues();
        List<String> bodies = bodyCaptor.getAllValues();

        assertTrue(emails.contains("sender@example.com"), "Should contain sender's email");
        assertTrue(emails.contains("receiver@example.com"), "Should contain receiver's email");
        
        // Formata o valor esperado usando o mesmo Locale do sistema, garantindo consistência
        String expectedAmountString = String.format("%.2f", amount);

        String senderBody = bodies.stream().filter(b -> b.contains("Você enviou")).findFirst().orElse("");
        String receiverBody = bodies.stream().filter(b -> b.contains("Você recebeu")).findFirst().orElse("");

        assertTrue(!senderBody.isEmpty(), "Sender email body should not be empty");
        assertTrue(senderBody.contains(expectedAmountString) && senderBody.contains("Receiver Name"), 
            "Sender email body content is incorrect. Expected amount: " + expectedAmountString);

        assertTrue(!receiverBody.isEmpty(), "Receiver email body should not be empty");
        assertTrue(receiverBody.contains(expectedAmountString) && receiverBody.contains("Sender Name"), 
            "Receiver email body content is incorrect. Expected amount: " + expectedAmountString);
    }

    @Test
    @DisplayName("Should throw exception when sender is not found")
    void shouldThrowExceptionWhenSenderNotFound() {
        // Arrange
        TransactionEvent event = new TransactionEvent(1L, 10L, 20L, new BigDecimal("100.50"), null, UUID.randomUUID());
        ConsumerRecord<String, TransactionEvent> record = new ConsumerRecord<>("transactions", 0, 0, "key", event);

        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            eventListener.handleTransactionEvent(record);
        });

        assertEquals("Usuário remetente não encontrado para o ID: 10", exception.getMessage());
        verify(emailService, never()).sendTransactionNotification(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw exception when receiver is not found")
    void shouldThrowExceptionWhenReceiverNotFound() {
        // Arrange
        TransactionEvent event = new TransactionEvent(1L, 10L, 20L, new BigDecimal("100.50"), null, UUID.randomUUID());
        ConsumerRecord<String, TransactionEvent> record = new ConsumerRecord<>("transactions", 0, 0, "key", event);

        User sender = mock(User.class);
        when(userRepository.findById(10L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(20L)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            eventListener.handleTransactionEvent(record);
        });

        assertEquals("Usuário destinatário não encontrado para o ID: 20", exception.getMessage());
        verify(emailService, never()).sendTransactionNotification(any(), any(), any());
    }
}
