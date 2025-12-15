package com.astropay.application.event.transactions;

import com.astropay.application.service.notification.EmailService;
import com.astropay.domain.model.user.Role;
import com.astropay.domain.model.user.User;
import com.astropay.domain.model.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionEventListenerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private UserRepository userRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks
    private TransactionEventListener eventListener;

    private User sender;
    private User receiver;
    private TransactionEvent transactionEvent;
    private String jsonPayload;

    @BeforeEach
    void setUp() throws Exception {
        sender = new User("Sender Name", "111", "sender@example.com", Role.ROLE_EMPLOYEE);
        ReflectionTestUtils.setField(sender, "id", 10L);

        receiver = new User("Receiver Name", "222", "receiver@example.com", Role.ROLE_EMPLOYEE);
        ReflectionTestUtils.setField(receiver, "id", 20L);

        transactionEvent = new TransactionEvent(1L, 10L, 20L, new BigDecimal("100.50"), LocalDateTime.now(), UUID.randomUUID());
        jsonPayload = objectMapper.writeValueAsString(transactionEvent);
    }

    @Test
    @DisplayName("Should handle transaction event and send emails to sender and receiver")
    void shouldHandleTransactionEvent() {
        // Arrange
        when(userRepository.findById(10L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(20L)).thenReturn(Optional.of(receiver));

        // Act
        eventListener.handleTransactionEvent(jsonPayload);

        // Assert
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).sendTransactionNotification(emailCaptor.capture(), anyString(), bodyCaptor.capture());

        List<String> emails = emailCaptor.getAllValues();
        List<String> bodies = bodyCaptor.getAllValues();

        assertTrue(emails.contains("sender@example.com"));
        assertTrue(emails.contains("receiver@example.com"));

        // Format the expected amount string in the same way as the application code
        String expectedAmountString = String.format("%.2f", transactionEvent.getAmount());

        String senderBody = bodies.stream().filter(b -> b.contains("Você enviou")).findFirst().orElse("");
        assertTrue(senderBody.contains(expectedAmountString) && senderBody.contains("Receiver Name"));

        String receiverBody = bodies.stream().filter(b -> b.contains("Você recebeu")).findFirst().orElse("");
        assertTrue(receiverBody.contains(expectedAmountString) && receiverBody.contains("Sender Name"));
    }

    @Test
    @DisplayName("Should throw exception when sender is not found")
    void shouldThrowExceptionWhenSenderNotFound() {
        // Arrange
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> eventListener.handleTransactionEvent(jsonPayload));
        assertTrue(exception.getMessage().contains("Falha ao desserializar ou processar o evento de transação"));
        
        verify(emailService, never()).sendTransactionNotification(any(), any(), any());
    }

    @Test
    @DisplayName("Should re-throw exception when email service fails")
    void shouldThrowExceptionWhenEmailServiceFails() {
        // Arrange
        when(userRepository.findById(10L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(20L)).thenReturn(Optional.of(receiver));
        doThrow(new RuntimeException("SMTP server down")).when(emailService).sendTransactionNotification(any(), any(), any());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> eventListener.handleTransactionEvent(jsonPayload));
        assertTrue(exception.getMessage().contains("Falha ao desserializar ou processar o evento de transação"));
    }
}
