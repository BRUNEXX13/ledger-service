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

import static org.junit.jupiter.api.Assertions.assertTrue;
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
        TransactionEvent event = new TransactionEvent(1L, 10L, 20L, new BigDecimal("100.50"), null, UUID.randomUUID());
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

        assertTrue(emails.contains("sender@example.com"));
        assertTrue(emails.contains("receiver@example.com"));
        
        String senderBody = bodies.stream().filter(b -> b.contains("You sent")).findFirst().orElse("");
        String receiverBody = bodies.stream().filter(b -> b.contains("You received")).findFirst().orElse("");

        assertTrue(senderBody.contains("100.50") && senderBody.contains("Receiver Name"));
        assertTrue(receiverBody.contains("100.50") && receiverBody.contains("Sender Name"));
    }
}
