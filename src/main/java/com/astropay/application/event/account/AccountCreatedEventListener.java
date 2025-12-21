package com.astropay.application.event.account;

import com.astropay.application.exception.AccountCreatedFailedException;
import com.astropay.application.service.notification.EmailService;
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

    public AccountCreatedEventListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @Transactional
    @KafkaListener(topics = "accounts", groupId = "${spring.kafka.consumer.group-id}")
    public void handleAccountCreatedEvent(@Payload AccountCreatedEvent event) {
        try {
            log.info("ACCOUNT CREATED event received for notification. AccountId: {}", event.getAccountId());

            String subject = "Welcome to Our Bank!";
            String body = String.format(
                    "Hello, %s! Your account has been successfully created. Your account ID is %d.",
                    event.getUserName(), event.getAccountId()
            );
            emailService.sendTransactionNotification(event.getUserEmail(), subject, body);
        } catch (Exception e) {
            throw new AccountCreatedFailedException("Failed to process account creation event for accountId: " + event.getAccountId(), e);
        }
    }
}
