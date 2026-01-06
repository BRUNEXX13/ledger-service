package com.bss.application.service.transaction;

import com.bss.application.dto.response.transaction.TransactionUserResponse;
import com.bss.application.exception.ResourceNotFoundException;
import com.bss.application.service.transaction.TransactionAuditServiceImpl;
import com.bss.domain.account.Account;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.bss.domain.transaction.Transaction;
import com.bss.domain.transaction.TransactionRepository;
import com.bss.domain.user.Role;
import com.bss.domain.user.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // Allows unused stubs in setUp
class TransactionAuditServiceImplTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ObjectMapper objectMapper;

    private TransactionAuditServiceImpl transactionAuditService;

    @BeforeEach
    void setUp() {
        transactionAuditService = new TransactionAuditServiceImpl(outboxEventRepository, transactionRepository, objectMapper);
    }

    private void setUserId(User user, Long id) {
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            ReflectionUtils.setField(idField, user, id);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    
    private void setAccountId(Account account, Long id) {
        try {
            Field idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            ReflectionUtils.setField(idField, account, id);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    
    private void setTransactionId(Transaction transaction) {
        try {
            Field idField = Transaction.class.getDeclaredField("id");
            idField.setAccessible(true);
            ReflectionUtils.setField(idField, transaction, 100L);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void createAuditEvent_ShouldSaveOutboxEvent() throws JsonProcessingException {
        // Arrange
        User senderUser = new User("Sender", "123", "sender@test.com", Role.ROLE_EMPLOYEE);
        setUserId(senderUser, 1L);
        Account sender = new Account(senderUser, BigDecimal.TEN);
        setAccountId(sender, 10L);

        User receiverUser = new User("Receiver", "456", "receiver@test.com", Role.ROLE_EMPLOYEE);
        setUserId(receiverUser, 2L);
        Account receiver = new Account(receiverUser, BigDecimal.ZERO);
        setAccountId(receiver, 20L);

        Transaction transaction = new Transaction(sender, receiver, BigDecimal.ONE, UUID.randomUUID());
        setTransactionId(transaction);

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"json\":\"payload\"}");

        // Act
        transactionAuditService.createAuditEvent(transaction, "TransactionCompleted");

        // Assert
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent savedEvent = captor.getValue();
        assertThat(savedEvent.getAggregateType()).isEqualTo("Transaction");
        assertThat(savedEvent.getAggregateId()).isEqualTo("100");
        assertThat(savedEvent.getEventType()).isEqualTo("TransactionCompleted");
        assertThat(savedEvent.getPayload()).isEqualTo("{\"json\":\"payload\"}");
    }

    @Test
    void findUserByTransactionId_ShouldReturnResponse_WhenTransactionExists() {
        // Arrange
        User senderUser = new User("Sender", "123", "sender@test.com", Role.ROLE_EMPLOYEE);
        setUserId(senderUser, 1L);
        Account sender = new Account(senderUser, BigDecimal.TEN);
        setAccountId(sender, 10L);

        User receiverUser = new User("Receiver", "456", "receiver@test.com", Role.ROLE_EMPLOYEE);
        setUserId(receiverUser, 2L);
        Account receiver = new Account(receiverUser, BigDecimal.ZERO);
        setAccountId(receiver, 20L);

        Transaction transaction = new Transaction(sender, receiver, BigDecimal.ONE, UUID.randomUUID());
        setTransactionId(transaction);

        when(transactionRepository.findById(100L)).thenReturn(Optional.of(transaction));

        // Act
        TransactionUserResponse response = transactionAuditService.findUserByTransactionId(100L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getTransactionId()).isEqualTo(100L);
        assertThat(response.getSenderName()).isEqualTo("Sender");
        assertThat(response.getSenderEmail()).isEqualTo("sender@test.com");
        assertThat(response.getSenderDocument()).isEqualTo("123");
    }

    @Test
    void findUserByTransactionId_ShouldThrowException_WhenTransactionNotFound() {
        // Arrange
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> transactionAuditService.findUserByTransactionId(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction not found with id: 999");
    }
}
