package com.bss.application.service.notification;

public interface EmailService {
    void sendTransactionNotification(String to, String subject, String body);
}
