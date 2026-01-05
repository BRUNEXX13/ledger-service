package com.bss.application.event.transactions;

import com.bss.application.service.notification.EmailService;
import com.bss.domain.model.user.Role;
import com.bss.domain.model.user.User;
import com.bss.domain.model.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionEventListenerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TransactionEventListener eventListener;

    private User sender;
    private User receiver;
    private TransactionEvent transactionEvent;

    @BeforeEach
    void setUp() {
        sender = new User("Sender Name", "111", "sender@example.com", Role.ROLE_EMPLOYEE);
        ReflectionTestUtils.setField(sender, "id", 10L);

        receiver = new User("Receiver Name", "222", "receiver@example.com", Role.ROLE_EMPLOYEE);
        ReflectionTestUtils.setField(receiver, "id", 20L);

        transactionEvent = new TransactionEvent(1L, 10L, 20L, new BigDecimal("100.50"), Instant.now(), UUID.randomUUID());
    }

    @Test
    @DisplayName("Should handle transaction event and send emails to sender and receiver")
    void shouldHandleTransactionEvent() {
        // Arrange
        when(userRepository.findById(10L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(20L)).thenReturn(Optional.of(receiver));

        // Act
        eventListener.handleTransactionEvent(transactionEvent);

        // Assert
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).sendTransactionNotification(emailCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture());

        List<String> emails = emailCaptor.getAllValues();
        List<String> bodies = bodyCaptor.getAllValues();

        assertTrue(emails.contains("sender@example.com"));
        assertTrue(emails.contains("receiver@example.com"));

        // Format the expected amount string using a specific Locale to avoid test failures on different machines
        String expectedAmountString = String.format(Locale.US, "%.2f", transactionEvent.getAmount());

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
        assertThrows(RuntimeException.class, () -> eventListener.handleTransactionEvent(transactionEvent));
        
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
        assertThrows(RuntimeException.class, () -> eventListener.handleTransactionEvent(transactionEvent));
    }
}
