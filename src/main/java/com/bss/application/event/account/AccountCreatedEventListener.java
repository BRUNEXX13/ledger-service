package com.bss.application.event.account;

import com.bss.application.exception.AccountCreatedFailedException;
import com.bss.application.service.notification.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountCreatedEventListener {

    private static final Logger log = LoggerFactory.getLogger(AccountCreatedEventListener.class);

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public AccountCreatedEventListener(EmailService emailService, ObjectMapper objectMapper) {
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @KafkaListener(topics = "accounts", groupId = "${spring.kafka.consumer.group-id}")
    public void handleAccountCreatedEvent(@Payload String jsonPayload) {
        try {
            AccountCreatedEvent event = objectMapper.readValue(jsonPayload, AccountCreatedEvent.class);
            
            log.info("ACCOUNT CREATED event received for notification. AccountId: {}", event.getAccountId());

            String subject = "Welcome to Our Bank!";
            String body = String.format(
                    "Hello, %s! Your account has been successfully created. Your account ID is %d.",
                    event.getUserName(), event.getAccountId()
            );
            emailService.sendTransactionNotification(event.getUserEmail(), subject, body);

        } catch (Exception e) {
            log.error("Error processing ACCOUNT CREATION event. Payload: {}. Error: {}", jsonPayload, e.getMessage(), e);
            throw new AccountCreatedFailedException(String.format("Failed to process account creation event from payload: %s", jsonPayload), e);
        }
    }
}
