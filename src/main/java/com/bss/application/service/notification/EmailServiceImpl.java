package com.bss.application.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Override
    public void sendTransactionNotification(String to, String subject, String body) {
        // In a real-world environment, this would be the logic for connecting to an
        // email provider (AWS SES, SendGrid, etc.)
        log.info("=================================================");
        log.info("SIMULE SEND EMAIL");
        log.info("To: {}", to);
        log.info("Subject: {}", subject);
        log.info("Body: {}", body);
        log.info("=================================================");
    }
}
