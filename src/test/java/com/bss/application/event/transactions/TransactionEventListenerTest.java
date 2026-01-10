package com.bss.application.event.transactions;

import com.bss.application.exception.ResourceNotFoundException;
import com.bss.application.exception.TransactionEventListenerException;
import com.bss.application.service.notification.EmailService;
import com.bss.domain.user.User;
import com.bss.domain.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionEventListenerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TransactionEventListener transactionEventListener;

    private TransactionEvent event;
    private String jsonPayload;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        event = new TransactionEvent(1L, 10L, 20L, BigDecimal.TEN, LocalDateTime.now(), UUID.randomUUID());
        jsonPayload = "{\"id\":1}";
        
        // Default behavior for objectMapper
        lenient().when(objectMapper.readValue(anyString(), eq(TransactionEvent.class))).thenReturn(event);
    }

    @Test
    @DisplayName("Should process transaction event successfully")
    void shouldProcessTransactionEventSuccessfully() {
        // Arrange
        User sender = mock(User.class);
        when(sender.getName()).thenReturn("Sender");
        when(sender.getEmail()).thenReturn("sender@test.com");

        User receiver = mock(User.class);
        when(receiver.getName()).thenReturn("Receiver");
        when(receiver.getEmail()).thenReturn("receiver@test.com");

        when(userRepository.findById(10L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(20L)).thenReturn(Optional.of(receiver));

        // Act
        transactionEventListener.handleTransactionEvent(jsonPayload);

        // Assert
        verify(emailService).sendTransactionNotification(eq("sender@test.com"), anyString(), anyString());
        verify(emailService).sendTransactionNotification(eq("receiver@test.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when sender not found")
    void shouldThrowExceptionWhenSenderNotFound() {
        // Arrange
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        // Act & Assert
        TransactionEventListenerException exception = assertThrows(TransactionEventListenerException.class, () -> 
            transactionEventListener.handleTransactionEvent(jsonPayload)
        );
        
        assertTrue(exception.getCause() instanceof ResourceNotFoundException);
        assertTrue(exception.getCause().getMessage().contains("Usuário remetente não encontrado"));
        verify(emailService, never()).sendTransactionNotification(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when receiver not found")
    void shouldThrowExceptionWhenReceiverNotFound() {
        // Arrange
        User sender = mock(User.class);
        when(userRepository.findById(10L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(20L)).thenReturn(Optional.empty());

        // Act & Assert
        TransactionEventListenerException exception = assertThrows(TransactionEventListenerException.class, () -> 
            transactionEventListener.handleTransactionEvent(jsonPayload)
        );
        
        assertTrue(exception.getCause() instanceof ResourceNotFoundException);
        assertTrue(exception.getCause().getMessage().contains("Usuário destinatário não encontrado"));
        verify(emailService, never()).sendTransactionNotification(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception on deserialization error")
    void shouldThrowExceptionOnDeserializationError() throws JsonProcessingException {
        // Arrange
        when(objectMapper.readValue(anyString(), eq(TransactionEvent.class))).thenThrow(new JsonProcessingException("Error") {});

        // Act & Assert
        TransactionEventListenerException exception = assertThrows(TransactionEventListenerException.class, () -> 
            transactionEventListener.handleTransactionEvent("invalid-json")
        );
        
        assertTrue(exception.getMessage().contains("Falha ao desserializar"));
    }
}
