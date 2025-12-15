package com.astropay.application.event.account;

import com.astropay.application.service.notification.EmailService;
import com.astropay.domain.model.account.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountCreatedEventListenerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountCreatedEventListener eventListener;

    @Test
    @DisplayName("Should handle account created event and send email notification")
    void shouldHandleAccountCreatedEvent() {
        // Arrange
        AccountCreatedEvent event = new AccountCreatedEvent(
                100L,
                1L,
                "John Doe",
                "john.doe@example.com",
                LocalDateTime.now()
        );

        // Act
        eventListener.handleAccountCreatedEvent(event);

        // Assert
        ArgumentCaptor<String> toCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

        verify(emailService).sendTransactionNotification(toCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture());

        assertTrue(subjectCaptor.getValue().contains("Welcome"));
        assertTrue(bodyCaptor.getValue().contains("John Doe"));
        assertTrue(bodyCaptor.getValue().contains("100"));
    }
}
